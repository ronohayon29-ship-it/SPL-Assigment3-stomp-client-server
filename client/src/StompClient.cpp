#include "../include/event.h"
#include <map>
#include <vector>
#include <fstream>
#include <algorithm>

#include <iostream>
#include <sstream>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <unordered_map>
#include <memory>

#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"
#include <MessageEvent.h>
#include <chrono>
#include <MessageEventParser.h>

using namespace std;


//parse host and port from string host:port
static bool splitHostPort(const std::string& hostport, std::string& host, short& port){
	size_t index = hostport.find(":");
	if(index == std::string::npos || index == 0 || index == hostport.size()-1){
		return false;
	}

	host = hostport.substr(0,index);
	std::string port_str = hostport.substr(index+1);

	if(host.empty() || port_str.empty()){
		return false;
	}

	int portInt = 0;
	try
	{
		portInt = std::stoi(port_str);
	}
	catch(const std::exception& e)
	{
		return false;
	}
	
	if(portInt < 1 || portInt > 65535){
		return false;
	}

	port = static_cast<short>(portInt);
	return true;
}


struct EventBrief {
    int half;
    int time;
    std::string name;
    std::string description;

    
    EventBrief()
        : half(0), time(0), name(""), description("") {}

    EventBrief(int h, int t, std::string n, std::string d)
        : half(h), time(t), name(std::move(n)), description(std::move(d)) {}
};

struct GameReport {
    std::string teamA;
    std::string teamB;

    //lex order map
    std::map<std::string, std::string> generalStats;
    std::map<std::string, std::string> teamAStats;
    std::map<std::string, std::string> teamBStats;

    std::vector<EventBrief> events;

    GameReport()
    : teamA(""), teamB(""), generalStats(), teamAStats(), teamBStats(), events() {}
};



class StompClient {
public:
    StompClient() :
        lock()
        , cv()
        , connection(nullptr)
        , shouldTerminate(false)
        , logged(false)
        , user("")
        , receiptId(0)
        , subId(0)
        , receipts()
        , subscriptions()
        , waiting(false)
        , loginSuccess(false)
        , reports() {}

    void start() {
		//Run a dedicated thread that continuously reads frames from the server socket
        thread socketThread(&StompClient::listenToServer, this);

        string line;
        while (!shouldTerminate && getline(cin, line)) {
            handleInput(line);
        }

        shouldTerminate = true;
        closeConn();
        socketThread.join();
    }

private:
    //sync
    mutex lock;
    condition_variable cv;

    //connection
    std::shared_ptr<ConnectionHandler> connection;

    //state
    atomic<bool> shouldTerminate;
    atomic<bool> logged;
    string user;

    //ids
    int receiptId;
    int subId;

	//maps for following the receiptions and subId's
    unordered_map<int, string> receipts;
    unordered_map<string, int> subscriptions;


    bool waiting;
    bool loginSuccess;

    unordered_map<string, unordered_map<string, GameReport>> reports;

private:
    int nextReceipt() { return ++receiptId; }
    int nextSub() { return ++subId; }


	//handle the input from the terminal
    void handleInput(const string& line) {
        istringstream iss(line);
        string cmd;
        iss >> cmd;

        if (cmd == "login") {
            string hostPort, user, pass;
            iss >> hostPort >> user >> pass;
            doLogin(hostPort, user, pass);
        }
        else if (cmd == "join") {
            string game;
            iss >> game;
            doJoin(game);
        }
        else if (cmd == "exit") {
            string game;
            iss >> game;
            doExit(game);
        }
        else if (cmd == "logout") {
            doLogout();
        }
        else if (cmd == "summary") {
            string game, reporter, outFile;
            iss >> game >> reporter >> outFile;
            doSummary(game, reporter, outFile);
        }
        else if (cmd == "report") {
            string file;
            iss >> file;
            doReport(file);
        }
        else if (cmd == "server_report") {
            doServerReport();
        }



    }


	//handle login command
    void doLogin(const string& hostPort, const string& u, const string& p) {
        {
        lock_guard<mutex> g(lock);
        if (connection) {
            cout << "The client is already logged in, log out before trying again" << endl;
            return;
        }
        }

        string host;
        short port;
        if (!splitHostPort(hostPort, host, port)) {
            cout << "Could not connect to server" << endl;
            return;
        }

        {
            lock_guard<mutex> g(lock);
            connection.reset(new ConnectionHandler(host, port));
            if (!connection->connect()) {
                connection.reset();
                cout << "Could not connect to server" << endl;
                return;
            }

            waiting = true;
            loginSuccess = false;

            string frame = StompProtocol::buildConnect("stomp.cs.bgu.ac.il", u, p);
            connection->sendFrameAscii(frame, '\0');
        }

        unique_lock<mutex> ul(lock);
		//Wait until the server responds to the login attempt (CONNECTED or ERROR)
		cv.wait(ul, [&]() {
    		return waiting == false;}); // lambada

        if (loginSuccess) {
            logged = true;
            user = u;
            cout << "Login successful" << endl;
        } else {
            closeConnLocked();
        }
    }


	//handle join command
    void doJoin(const string& game) {
        lock_guard<mutex> g(lock);

        if (!logged || !connection) {
            cout << "You are not logged in" << endl;
            return;
        }
        if (subscriptions.count(game)) {
            cout << "Already joined channel " << game << endl;
            return;
        }

        int sId = nextSub();
        int rId = nextReceipt();

        subscriptions[game] = sId;
        receipts[rId] = "join " + game;

        string frame = StompProtocol::buildSubscribe("/" + game, sId, rId);
        connection->sendFrameAscii(frame, '\0');
    }


	//handle exit (unsubscribe) command
    void doExit(const string& game) {
        lock_guard<mutex> g(lock);

        if (!logged || !connection) {
            cout << "You are not logged in" << endl;
            return;
        }
        if (!subscriptions.count(game)) {
            cout << "Not subscribed to channel " << game << endl;
            return;
        }

        int sId = subscriptions[game];
        int rId = nextReceipt();

        receipts[rId] = "exit " + game;

        string frame = StompProtocol::buildUnsubscribe(sId, rId);
        connection->sendFrameAscii(frame, '\0');
    }

	//handle logout (disconnect) command
    void doLogout() {
        lock_guard<mutex> g(lock);

        if (!logged || !connection) {
            cout << "You are not logged in" << endl;
            return;
        }

        int rId = nextReceipt();
        receipts[rId] = "logout";

        string frame = StompProtocol::buildDisconnect(rId);
        connection->sendFrameAscii(frame, '\0');
    }


	//socket thread listens to the server
    void listenToServer() {
        while (!shouldTerminate) {
        std::shared_ptr<ConnectionHandler> c;
        {
            std::lock_guard<std::mutex> g(lock);
            c = connection;            // מחזיק refcount
        }

        if (!c) {
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            continue;
        }

        std::string msg;
        if (!c->getFrameAscii(msg, '\0')) {
            std::lock_guard<std::mutex> g(lock);
            closeConnLocked();         // עושה connection.reset() וסגירה פנימית
            logged = false;
            continue;
        }

        StompFrame f = StompProtocol::parseFrame(msg);
        handleServerFrame(f, msg);
    }
}


	//handle the frame
    void handleServerFrame(const StompFrame& f, const string& raw) {
        lock_guard<mutex> g(lock);

        if (f.command == "CONNECTED") {
            waiting = false;
            loginSuccess = true;
            cv.notify_all();
        }
        else if (f.command == "ERROR") {
            auto it = f.headers.find("message");
            cout << (it != f.headers.end() ? it->second : raw) << endl;
            waiting = false;
            loginSuccess = false;
            cv.notify_all();
            closeConnLocked();
            logged = false;
        }
        else if (f.command == "RECEIPT") {
            int id = stoi(f.headers.at("receipt-id"));
            if (!receipts.count(id)) return;

            string action = receipts[id];
            receipts.erase(id);

            if (action.find("join") == 0) {
                cout << "Joined channel " << action.substr(5) << endl;
            }
            else if (action.find("exit") == 0) {
                string gname = action.substr(5);
                subscriptions.erase(gname);
                cout << "Exited channel " << gname << endl;
            }
            else if (action == "logout") {
                subscriptions.clear();
                receipts.clear();
                waiting = false;
                loginSuccess = false;

                logged = false;
                closeConnLocked();

                cout << "Logged out" << endl;
            }
        }
    
        //handle message
        else if (f.command == "MESSAGE") {

        MessageEvent me = MessageEventParser::parse(f.body);
        
        //if the user was the one who sent it he doesn't need to save it again 
        if (me.user == user) {
            return;
}

        string game = me.teamA + "_" + me.teamB;
        string reporter = me.user;

        GameReport& r = reports[game][reporter];
        if (r.teamA.empty()) {
            r.teamA = me.teamA;
            r.teamB = me.teamB;
        }

        //READ state from persistent report
        bool stateBefore = true;
        auto itOld = r.generalStats.find("before halftime");
        if (itOld != r.generalStats.end()) {
            stateBefore = (itOld->second == "true");
        }

        int eventHalf = stateBefore ? 0 : 1;

        //accumulate updates
        for (const auto& kv : me.generalUpdates) r.generalStats[kv.first] = kv.second;
        for (const auto& kv : me.teamAUpdates)  r.teamAStats[kv.first] = kv.second;
        for (const auto& kv : me.teamBUpdates)  r.teamBStats[kv.first] = kv.second;

        //WRITE state ONLY if this message contains "before halftime"
        auto itNew = me.generalUpdates.find("before halftime");
        if (itNew != me.generalUpdates.end()) {
            r.generalStats["before halftime"] = itNew->second;
        }

        //store the event
        EventBrief eb;
        eb.half = eventHalf;
        eb.time = me.time;
        eb.name = me.eventName;
        eb.description = me.description;
        r.events.push_back(eb);
        }
    }

	//Safely close the connection and release the socket, wakes up the socket thread
    void closeConn() {
        lock_guard<mutex> g(lock);
        closeConnLocked();
    }

    void closeConnLocked() {
        if (connection) {
            connection->close();
            connection.reset();
        }
    }


    //summary the reports
    void doSummary(const string& game, const string& reporter, const string& outFile) {
    lock_guard<mutex> g(lock);

    if (!logged) {
        cout << "You are not logged in" << endl;
        return;
    }

    //firsty, checking if there is any valid info
    auto itG = reports.find(game);
    if (itG == reports.end() || !itG->second.count(reporter)) {
        cout << "No reports for " << game << " from " << reporter << endl;
        return;
    }

    GameReport& r = itG->second[reporter];

    //sort the reports by time
    vector<EventBrief> ev = r.events;
    sort(ev.begin(), ev.end(), [](const EventBrief& a, const EventBrief& b) {
        if (a.half != b.half) return a.half < b.half;
        return a.time < b.time;
    });

    //open output file
    ofstream out(outFile);
    if (!out.is_open()) {
        cout << "Could not open file " << outFile << endl;
        return;
    }


    //all this section is about organize the output file
    out << r.teamA << " vs " << r.teamB << "\n";
    out << "Game stats:\n";

    out << "General stats:\n";
    for (const auto& kv : r.generalStats) out << kv.first << ": " << kv.second << "\n";

    out << r.teamA << " stats:\n";
    for (const auto& kv : r.teamAStats) out << kv.first << ": " << kv.second << "\n";

    out << r.teamB << " stats:\n";
    for (const auto& kv : r.teamBStats) out << kv.first << ": " << kv.second << "\n";

    out << "Game event reports:\n";
    for (const auto& e : ev) {
        out << e.time << " - " << e.name << ":\n\n";
        out << e.description << "\n\n\n";
    }

    out.close();
    cout << "Summary written to " << outFile << endl;
}

//handle report
void doReport(const string& file) {
    lock_guard<mutex> g(lock);

    if (!logged || !connection) {
        cout << "You are not logged in" << endl;
        return;
    }

    names_and_events nne;
    try {
        nne = parseEventsFile(file);
    } catch (...) {
        cout << "Could not parse events file " << file << endl;
        return;
    }

    // game name format: TeamA_TeamB (same as join)
    string game = nne.team_a_name + "_" + nne.team_b_name;

    // require join before report
    if (!subscriptions.count(game)) {
        cout << "Not subscribed to channel " << game << endl;
        return;
    }

    // create/get report for (game, user)
    GameReport& r = reports[game][user];
    r.teamA = nne.team_a_name;
    r.teamB = nne.team_b_name;

    // half state (true = first half, false = second half)
    bool beforeHalftime = true;

    
    for (const Event& e : nne.events) {

        //decide event half based on current state BEFORE applying this event's halftime update
        int eventHalf = beforeHalftime ? 0 : 1;

        //update accumulated stats
        for (const auto& kv : e.get_game_updates()) {
            r.generalStats[kv.first] = kv.second;
        }
        for (const auto& kv : e.get_team_a_updates()) {
            r.teamAStats[kv.first] = kv.second;
        }
        for (const auto& kv : e.get_team_b_updates()) {
            r.teamBStats[kv.first] = kv.second;
        }

        //store event locally for summary
        EventBrief eb;
        eb.half = eventHalf;
        eb.time = e.get_time();
        eb.name = e.get_name();
        eb.description = e.get_discription();
        r.events.push_back(eb);

        //AFTER storing the event, update halftime state for next events (if present)
        auto itBH = e.get_game_updates().find("before halftime");
        if (itBH != e.get_game_updates().end()) {
            beforeHalftime = (itBH->second == "true");
        }

        //build SEND body and send to server
        ostringstream body;
        body << "user: " << user << "\n";
        body << "team a: " << nne.team_a_name << "\n";
        body << "team b: " << nne.team_b_name << "\n";
        body << "event name: " << e.get_name() << "\n";
        body << "time: " << e.get_time() << "\n";

        body << "general game updates:\n";
        for (const auto& kv : e.get_game_updates()) {
            body << kv.first << ": " << kv.second << "\n";
        }

        body << "team a updates:\n";
        for (const auto& kv : e.get_team_a_updates()) {
            body << kv.first << ": " << kv.second << "\n";
        }

        body << "team b updates:\n";
        for (const auto& kv : e.get_team_b_updates()) {
            body << kv.first << ": " << kv.second << "\n";
        }

        body << "description:\n" << e.get_discription() << "\n";

        string frame = StompProtocol::buildSend("/" + game, body.str(),file);
        connection->sendFrameAscii(frame, '\0');
    }

    cout << "Reported " << nne.events.size() << " events to " << game << endl;
}

//ours - we added a server report function in order to print the server report from the DataBase
void doServerReport() {
    lock_guard<mutex> g(lock);

    if (!logged || !connection) {
        cout << "You are not logged in" << endl;
        return;
    }

    string frame =
        "SEND\n"
        "destination:/admin/report\n"
        "\n"
        "\0";

    connection->sendFrameAscii(frame, '\0');

    cout << "Requested server report" << endl;
}


};

int main() {
    StompClient c;
    c.start();
    return 0;

}