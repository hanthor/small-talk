package app.dapk.st.core

interface Preferences {

    suspend fun store(key: String, value: String)
    suspend fun store(key: String, value: Set<String>)
    suspend fun readString(key: String): String?
    suspend fun readStrings(key: String): Set<String>?
    suspend fun clear()
    suspend fun remove(key: String)
}

interface CachedPreferences : Preferences {
    suspend fun readString(key: String, defaultValue: String): String
}

suspend fun CachedPreferences.readBoolean(key: String, defaultValue: Boolean) = this
    .readString(key, defaultValue.toString())
    .toBooleanStrict()

suspend fun Preferences.readBoolean(key: String) = this.readString(key)?.toBooleanStrict()
suspend fun Preferences.store(key: String, value: Boolean) = this.store(key, value.toString())
suspend fun Preferences.append(key: String, value: String) {
    val current = this.readStrings(key) ?: emptySet()
    this.store(key, current + value)
}

suspend fun Preferences.removeFromSet(key: String, value: String) {
    val current = this.readStrings(key) ?: emptySet()
    this.store(key, current - value)
}

