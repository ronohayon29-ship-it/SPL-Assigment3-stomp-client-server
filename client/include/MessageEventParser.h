#pragma once
#include <string>
#include "MessageEvent.h"

class MessageEventParser {
    /**
 * Parses the textual MESSAGE body format used in the assignment into a MessageEvent.
 *
 * Expected body format:
 *  user: <username>
 *  team a: <teamA>
 *  team b: <teamB>
 *  event name: <eventName>
 *  time: <int>
 *  general game updates:
 *    key: value
 *  team a updates:
 *    key: value
 *  team b updates:
 *    key: value
 *  description:
 *    free text (possibly multiple lines)
 *
 * Note: "before halftime" is just a key in general updates.
 *       The "carry-forward" logic when it's missing is handled in StompClient.
 */
public:
    //Parse a MESSAGE body into MessageEvent.
    static MessageEvent parse(const std::string& body);
    static std::string trim(const std::string& s);
    static bool startsWith(const std::string& s, const std::string& p);

private:
    
};