# Methods called from JavaScript in the entry WebView.
-keepclassmembers class app.stemma.ui.entry.EntryJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# jsoup references optional re2j support that is not bundled.
-dontwarn com.google.re2j.**
