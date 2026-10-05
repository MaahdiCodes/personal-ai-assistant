# SQLCipher's native code calls back into these classes (JNI), so R8 must not rename or remove them.
-keep,includedescriptorclasses class net.zetetic.database.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.zetetic.database.sqlcipher.** { *; }

# LiteRT-LM (the on-device AI runtime) ships no R8 rules of its own. Its native code calls back into
# these classes by name (JNI: callbacks, exceptions, results), so R8 must not rename or remove them.
# Not kept: ReflectionTool and ToolKt, the only users of kotlin-reflect (about 1,100 classes). They
# serve tool calling, which Mavick doesn't use, so R8 drops them and kotlin-reflect with them.
-keep,includedescriptorclasses class !com.google.ai.edge.litertlm.ReflectionTool*,!com.google.ai.edge.litertlm.ToolKt*,com.google.ai.edge.litertlm.** { *; }
-keep,includedescriptorclasses interface com.google.ai.edge.litertlm.** { *; }
