# NumiPad Junior R8 rules.
# Room, DataStore, Navigation and Compose ship their own consumer rules.
# Enums are persisted by name(); keep their names stable under R8.
-keepclassmembers enum com.numipad.junior.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}
