package fr.geoking.julius.shared.util

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ZipUtils {
    fun unzip(zipFile: File, targetDir: File) {
        if (!targetDir.exists()) targetDir.mkdirs()

        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                val canonicalTargetDir = targetDir.canonicalPath
                val canonicalNewFile = newFile.canonicalPath
                if (!canonicalNewFile.startsWith(canonicalTargetDir + File.separator)) {
                    throw Exception("Zip entry is outside of the target directory")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
