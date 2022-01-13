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
     * 写入App名称到xml
     */
    public static void writeApplicationName(String inManifestFile, String outManifestFile, String newApplicationName){
        ModificationProperty property = new ModificationProperty();
        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.NAME,newApplicationName));

        FileProcesser.processManifestFile(inManifestFile, outManifestFile, property);

    }

    /**
     * 写入appComponentFactory到xml
     */
    public static void writeAppComponentFactory(String inManifestFile, String outManifestFile, String newComponentFactory){
        ModificationProperty property = new ModificationProperty();
        property.addApplicationAttribute(new AttributeItem("appComponentFactory",newComponentFactory));

        FileProcesser.processManifestFile(inManifestFile, outManifestFile, property);

    }

    /**
     * 获取AndroidManifest.xml中属性的值
     * @param file AndroidManifest.xml文件
     * @param tag 标签
     * @param ns 命名空间
     * @param attrName 属性名
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
     * 获取AndroidManifest.xml里的android:name
     */
    public static String getApplicationName(String file) {
        return getValue(file,"application","android","name");
    }

    /**
     * 获取AndroidManifest.xml里的android:appComponentFactory
     */
    public static String getAppComponentFactory(String file) {
        return getValue(file,"application","android","appComponentFactory");
    }

    public static String getPackageName(String file) {
        return getValue(file,"manifest","android","package");
    }

}
