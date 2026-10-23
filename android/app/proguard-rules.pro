# kotlinx.serialization — mantém os serializadores gerados.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class ao.kupila.gestaonegocios.** {
    *** Companion;
}
-keepclasseswithmembers class ao.kupila.gestaonegocios.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ao.kupila.gestaonegocios.**$$serializer { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes Signature, Exceptions

# Sem registos de depuração no APK de produção.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
