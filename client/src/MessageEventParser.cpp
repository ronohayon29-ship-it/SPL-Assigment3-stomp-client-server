#include "../include/MessageEventParser.h"
#include <sstream>

using namespace std;


//Removes leading and trailing spaces only.
string MessageEventParser::trim(const string& s) {
    size_t b = s.find_first_not_of(' ');
    if (b == string::npos) return "";
    size_t e = s.find_last_not_of(' ');
    return s.substr(b, e - b + 1);
}


//Returns true if s begins with prefix.
bool MessageEventParser::startsWith(const string& s, const string& prefix) {
    return s.rfind(prefix, 0) == 0;
}


//Extract the substring after the first ':' in the line.
static string valueAfterColon(const string& line) {
    size_t c = line.find(':');
    if (c == string::npos) return "";
    return MessageEventParser::trim(line.substr(c + 1));
}


//Parses a "key: value" line into a map.
static void parseKeyValueLine(const string& line, map<string,string>& out) {
    size_t c = line.find(':');
    if (c == string::npos) return;

    string key = MessageEventParser::trim(line.substr(0, c));
    string val = MessageEventParser::trim(line.substr(c + 1));

    if (!key.empty()) out[key] = val;
}



MessageEvent MessageEventParser::parse(const string& body) {
    MessageEvent ev;

    istringstream in(body);
    string line;

    //We track which section we are currently parsing.
    enum Section { NONE, GENERAL, TEAM_A, TEAM_B, DESC };
    Section section = NONE;

    // ---------------------------
    // Part 1: Parse the fixed fields (user/team/event/time) until we reach a section title
    // ---------------------------
    while (getline(in, line)) {
        string t = trim(line);
        if (t.empty()) continue;

        if (startsWith(t, "user:")) {
            ev.user = valueAfterColon(t);
            continue;
        }
        if (startsWith(t, "team a:")) {
            ev.teamA = valueAfterColon(t);
            continue;
        }
        if (startsWith(t, "team b:")) {
            ev.teamB = valueAfterColon(t);
            continue;
        }
        if (startsWith(t, "event name:")) {
            ev.eventName = valueAfterColon(t);
            continue;
        }
        if (startsWith(t, "time:")) {
            try { ev.time = stoi(valueAfterColon(t)); }
            catch (...) { ev.time = 0; }
            continue;
        }

        //Section titles
        if (startsWith(t, "general game updates:")) { section = GENERAL; break; }
        if (startsWith(t, "team a updates:"))       { section = TEAM_A;  break; }
        if (startsWith(t, "team b updates:"))       { section = TEAM_B;  break; }
        if (startsWith(t, "description:"))          { section = DESC;    break; }
    }

    // ---------------------------
    // Part 2: Parse the sections
    // ---------------------------
    string desc;

    while (getline(in, line)) {
        string t = trim(line);

        //Handle section switches
        if (startsWith(t, "general game updates:")) { section = GENERAL; continue; }
        if (startsWith(t, "team a updates:"))       { section = TEAM_A;  continue; }
        if (startsWith(t, "team b updates:"))       { section = TEAM_B;  continue; }
        if (startsWith(t, "description:"))          { section = DESC;    continue; }

        //Description: keep raw lines
        if (section == DESC) {
            if (!desc.empty()) desc += "\n";
            desc += line;
            continue;
        }

        //Updates: skip empty lines
        if (t.empty()) continue;

        if (section == GENERAL) parseKeyValueLine(t, ev.generalUpdates);
        else if (section == TEAM_A) parseKeyValueLine(t, ev.teamAUpdates);
        else if (section == TEAM_B) parseKeyValueLine(t, ev.teamBUpdates);
        else {
            //If section == NONE and we got content, ignore it
        }
    }

    auto it = ev.generalUpdates.find("before halftime");
    if (it != ev.generalUpdates.end()) {
        ev.beforeHalftime = (it->second == "true");
    }
    ev.description = trim(desc);
    return ev;
}