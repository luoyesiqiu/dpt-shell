package com.luoye.dpt.util;

import com.wind.meditor.core.FileProcesser;
import com.wind.meditor.core.ManifestEditor;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;
import pxb.android.axml.AxmlParser;

/**
 * @author luoyesiqiu
 */
public class ManifestUtils {

    /**
     * Write app name to xml
     */
    public static void writeApplicationName(String inManifestFile, String outManifestFile, String newApplicationName){
        ModificationProperty property = new ModificationProperty();
        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.NAME,newApplicationName));

        FileProcesser.processManifestFile(inManifestFile, outManifestFile, property);

    }

    /**
     * Write appComponentFactory to xml
     */
    public static void writeAppComponentFactory(String inManifestFile, String outManifestFile, String newComponentFactory){
        ModificationProperty property = new ModificationProperty();
        property.addApplicationAttribute(new AttributeItem("appComponentFactory",newComponentFactory));

        FileProcesser.processManifestFile(inManifestFile, outManifestFile, property);

    }

    /**
     * Write debuggable field
     */
    public static void writeDebuggable(String inManifestFile, String outManifestFile, String debuggable){
        ModificationProperty property = new ModificationProperty();
        property.addApplicationAttribute(new AttributeItem("debuggable",debuggable));

        FileProcesser.processManifestFile(inManifestFile, outManifestFile, property);

    }

    /**
     * Get AndroidManifest.xml attr value
     * @param file AndroidManifest.xml file
     * @param tag tag name
     * @param ns namespace
     * @param attrName attr name
     * @return
     */
    public static String getValue(String file,String tag,String ns,String attrName){
        byte[] axmlData = IoUtils.readFile(file);
        AxmlParser axmlParser = new AxmlParser(axmlData);
        try {
            while (axmlParser.next() != AxmlParser.END_FILE) {
                if (axmlParser.getAttrCount() != 0 && !axmlParser.getName().equals(tag)) {
                    continue;
                }
                for (int i = 0; i < axmlParser.getAttrCount(); i++) {
                    if (axmlParser.getNamespacePrefix().equals(ns) && axmlParser.getAttrName(i).equals(attrName)) {
                        return (String) axmlParser.getAttrValue(i);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get android:name value from AndroidManifest.xml
     */
    public static String getApplicationName(String file) {
        return getValue(file,"application","android","name");
    }

    /**
     * Get android:appComponentFactory value from AndroidManifest.xml
     */
    public static String getAppComponentFactory(String file) {
        return getValue(file,"application","android","appComponentFactory");
    }

    public static String getPackageName(String file) {
        return getValue(file,"manifest","android","package");
    }

}
