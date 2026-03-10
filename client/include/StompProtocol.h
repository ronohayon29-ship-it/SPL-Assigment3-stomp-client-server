#pragma once
#include <string>
#include <unordered_map>

#include "../include/ConnectionHandler.h"

// TODO: implement the STOMP protocol

struct StompFrame{
    std::string command;
    std::unordered_map<std::string,std::string> headers;
    std::string body;

        StompFrame() : command(""), headers(), body("") {}
};

class StompProtocol
{
public:
    // build connect string frame
    static std::string buildConnect(const std::string& host, const std::string& user, const std::string& pass);

    // build subscribe string frame
    static std::string buildSubscribe(const std::string& des, int subId, int receiptId = -1);

    // build unsubscribe string frame
    static std::string buildUnsubscribe(int subId, int recieptId = -1);

    //build disconnect string frame
    static std::string buildDisconnect(int receiptId = -1);

    //build send string frame
    static std::string buildSend(const std::string& des,const std::string& body, const std::string& filename,  int receiptId = -1);

    //parse the frame
    static StompFrame parseFrame(const std::string& raw);
};
