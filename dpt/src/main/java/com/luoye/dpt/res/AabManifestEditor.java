package com.luoye.dpt.res;

/**
 * @author luoyesiqiu
 */
public class AabManifestEditor {

    public static void writeApplicationExtractNativeLibs(String inManifestFile, String outManifestFile, String newExtractNativeLibs) {
        try {
            AndroidResourcesEditor.putAttribute(inManifestFile, outManifestFile,
                    "application",
                    "extractNativeLibs",
                    newExtractNativeLibs);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * Write app name to xml
     */
    public static void writeApplicationName(String inManifestFile, String outManifestFile, String newApplicationName) {
        try {
            AndroidResourcesEditor.putAttribute(inManifestFile, outManifestFile,
                    "application",
                    "name",
                    newApplicationName);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Write appComponentFactory to xml
     */
    public static void writeAppComponentFactory(String inManifestFile, String outManifestFile, String newComponentFactory){
        try {
            AndroidResourcesEditor.putAttribute(inManifestFile, outManifestFile,
                    "application",
                    "appComponentFactory",
                    newComponentFactory);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Write debuggable field
     */
    public static void writeDebuggable(String inManifestFile, String outManifestFile, String debuggable){
        try {
            AndroidResourcesEditor.putAttribute(inManifestFile, outManifestFile,
                    "application",
                    "debuggable",
                    debuggable);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get AndroidManifest.xml attr value
     * @param file AndroidManifest.xml file
     */
    public static String getAttributeValue(String file, String elementName, String attrName) {
        try {
            return AndroidResourcesEditor.getAttributeValue(file, elementName, attrName);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get android:name value from AndroidManifest.xml
     */
    public static String getApplicationName(String file) {
        return getAttributeValue(file,"application","name");
    }

    /**
     * Get android:appComponentFactory value from AndroidManifest.xml
     */
    public static String getAppComponentFactory(String file) {
        return getAttributeValue(file,"application","appComponentFactory");
    }

    public static String getPackageName(String file) {
        return getAttributeValue(file,"manifest","package");
    }
}
