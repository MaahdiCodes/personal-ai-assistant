# SQLCipher's native code calls back into these classes (JNI), so R8 must not rename or remove them.
-keep,includedescriptorclasses class net.zetetic.database.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.zetetic.database.sqlcipher.** { *; }
