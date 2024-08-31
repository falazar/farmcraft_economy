package com.falazar.farmupcraft.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.net.URL;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;

public class FileInfo {

    public static String getJarModificationTime(Class<?> clazz) {
        try {
            URL url = clazz.getProtectionDomain().getCodeSource().getLocation();
            String jarPath = URLDecoder.decode(url.getPath(), "UTF-8");
            File jarFile = new File(jarPath);
            if (jarFile.exists()) {
                long lastModified = jarFile.lastModified();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                return sdf.format(lastModified);
            } else {
                return "File not found";
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "Error decoding URL";
        }
    }
}
