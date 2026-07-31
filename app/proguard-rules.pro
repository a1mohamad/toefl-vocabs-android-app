# kotlinx.serialization keeps its generated serializers on the classes
# themselves; R8 has to be told not to strip them or every progress file and
# settings blob fails to decode in a release build.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class io.github.a1mohamad.toeflvocab.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.a1mohamad.toeflvocab.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.a1mohamad.toeflvocab.**$$serializer { *; }
