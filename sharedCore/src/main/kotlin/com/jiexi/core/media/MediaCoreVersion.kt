package com.jiexi.core.media

/** Compare stable and nightly date versions without downgrading a newer installed core. */
object MediaCoreVersion {
    fun shouldInstallBundled(installed: String?, bundled: String): Boolean {
        val current = parts(installed) ?: return true
        val target = parts(bundled) ?: return false
        for (index in 0 until maxOf(current.size, target.size)) {
            val difference = (current.getOrElse(index) { 0L }).compareTo(target.getOrElse(index) { 0L })
            if (difference != 0) return difference < 0
        }
        return false
    }

    private fun parts(version: String?): List<Long>? = version?.trim()
        ?.takeIf { it.matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2}(?:\\.\\d+)?")) }
        ?.split('.')?.map { it.toLong() }
}
