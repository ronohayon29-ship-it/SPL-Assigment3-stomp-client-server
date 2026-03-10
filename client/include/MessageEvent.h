#pragma once
#include <string>
#include <map>

struct MessageEvent {
    std::string user;      //from "user:" line in body
    std::string teamA;
    std::string teamB;

    std::string eventName;
    int time = 0;          //in seconds

    bool beforeHalftime = true; //before half time state

    //updates sorted to general and teams separately
    std::map<std::string, std::string> generalUpdates;
    std::map<std::string, std::string> teamAUpdates;
    std::map<std::string, std::string> teamBUpdates;

    std::string description;

    MessageEvent()
    : user(""), teamA(""), teamB(""), eventName(""),
        generalUpdates(), teamAUpdates(), teamBUpdates(),
        description("") {}
};