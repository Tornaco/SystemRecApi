package dev.nick.library;

interface IToken {
    String getDescription();
    void onDeny();
    void onAllow();
    void onAllowRemember();
}
