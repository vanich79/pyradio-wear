# kotlinx.serialization генерирует сериализаторы как статические поля классов;
# без этого правила R8 срезает их вместе с разбором stations.json.
-keepclassmembers class com.pyradio.wear.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.pyradio.wear.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
