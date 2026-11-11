-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontwarn kotlinx.serialization.**
-keep,includedescriptorclasses class com.superman.lianji.**$$serializer { *; }
-keepclassmembers class com.superman.lianji.** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
