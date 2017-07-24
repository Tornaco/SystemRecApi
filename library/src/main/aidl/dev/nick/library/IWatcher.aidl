// IWatcher.aidl
package dev.nick.library;

// Declare any non-default types here with import statements

interface IWatcher {
   void onStart();
   void onStop();
   void onElapsedTimeChange(String formatedTime);
}
