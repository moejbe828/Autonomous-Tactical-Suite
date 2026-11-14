
# ATS ProGuard Configuration

# Keep annotation metadata
-keepattributes *Annotation*

# Preserve source information for debugging
-keepattributes SourceFile,LineNumberTable

# ATS TRACK SCAN - Google libphonenumber
-keep class com.google.i18n.phonenumbers.** { *; }
