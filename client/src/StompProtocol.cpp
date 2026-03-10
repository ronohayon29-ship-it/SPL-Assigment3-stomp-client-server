#include "../include/StompProtocol.h"

#include <sstream>
#include <vector>


//spilt the lines of raw message
static std::vector<std::string> splitLines(const std::string& s){
    std::vector<std::string> out;
    std::istringstream iss(s);

    std::string line;
    while(std::getline(iss,line)){
        out.push_back(line);
    }

    return out;
}

//parse raw message into a StompFrame
StompFrame StompProtocol::parseFrame(const std::string& raw){
    StompFrame f;

    //separate headers and body by first blank line "\n\n"
    size_t sep = raw.find("\n\n");
    
    std::string headerPart;
    std::string bodyPart;
    if(sep == std::string::npos){ // if sep==std::string::npos it means that the string we searched doesn't exist
        headerPart = raw;
        bodyPart = "";
    }
    else{
        headerPart = raw.substr(0, sep);
        bodyPart = raw.substr(sep + 2);
    }


    auto lines = splitLines(headerPart);
    if (lines.empty()) return f;

    f.command = lines[0];

    for (size_t i = 1; i < lines.size(); i++) {
        if (lines[i].empty()) continue;
        size_t c = lines[i].find(':');
        if (c == std::string::npos) continue;
        std::string key = lines[i].substr(0, c);
        std::string val = lines[i].substr(c + 1);
        f.headers[key] = val;
    }

    f.body = bodyPart;
    return f;
}

// build connect frame to send to the server
std::string StompProtocol::buildConnect(const std::string& host, const std::string& user, const std::string& pass){
    std::string frame =
    "CONNECT\n"
    "accept-version:1.2\n"
    "host:" + host + "\n"
    "login:" + user + "\n"
    "passcode:" + pass + "\n"
    "\n";
    return frame;
}


//bulid subscribe frame
std::string StompProtocol::buildSubscribe(const std::string& des, int subId, int receiptId){
    std::string frame = 
    "SUBSCRIBE\n"
    "destination:" + des + "\n"
    "id:" + std::to_string(subId) + "\n";

    if(receiptId != -1){
        frame += ("receipt:" + std::to_string(receiptId) + "\n");
    }

    frame += "\n";

    return frame;
}

//bulid unsubscribe frame
std::string StompProtocol::buildUnsubscribe(int subId, int receiptId){
    std::string frame = 
    "UNSUBSCRIBE\n"
    "id:" + std::to_string(subId) + "\n";

    if(receiptId != -1){
        frame += ("receipt:" + std::to_string(receiptId) + "\n");
    }

    frame += "\n";

    return frame;
}

//bulid disconnect frame
std::string StompProtocol::buildDisconnect(int receiptId){
    std::string frame = 
    "DISCONNECT\n";

    if(receiptId != -1){
        frame += ("receipt:" + std::to_string(receiptId) + "\n");
    }

    frame += "\n";

    return frame;
}


//bulid send frame
std::string StompProtocol::buildSend(const std::string& des,const std::string& body, const std::string& filename, int receiptId){
    std::string frame = 
    "SEND\n"
    "destination:" + des + "\n"
    "filename:" + filename + "\n";

    if(receiptId != -1){
        frame += ("receipt:" + std::to_string(receiptId) + "\n");
    }

    frame += "\n";
    frame += body;
    return frame;
}