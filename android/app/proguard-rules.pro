# Preserve line numbers for Play Console deobfuscation (with bundled mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep the WebView JavascriptInterface bridge class public and unmerged under R8 full mode.
-keepattributes RuntimeVisibleAnnotations
-keep class com.exchip.offzone.OffzoneMapBridge {
    @android.webkit.JavascriptInterface public <methods>;
}
-keepclasseswithmembers class com.exchip.offzone.** {
    @android.webkit.JavascriptInterface <methods>;
}

# FocusAccessibilityService compares event.className against MainActivity::class.java.name.
-keep class com.exchip.offzone.MainActivity {
    public <init>();
}

# Reflective Credentials Play Services provider declared in merged manifest meta-data.
-keep class androidx.credentials.playservices.CredentialProviderPlayServicesImpl {
    public <init>(android.content.Context);
}
