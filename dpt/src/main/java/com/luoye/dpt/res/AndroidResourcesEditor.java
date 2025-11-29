package com.luoye.dpt.res;

import com.android.aapt.Resources;
import com.luoye.dpt.util.IoUtils;
import com.luoye.dpt.util.LogUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author luoyesiqiu
 */
public class AndroidResourcesEditor {

    public static final int DEBUGGABLE_RESOURCE_ID = 0x0101000f;
    public static final int EXTRACT_NATIVE_LIBS_RESOURCE_ID = 0x10104ea;
    public static final int NAME_RESOURCE_ID = 0x01010003;
    public static final int APP_COMPONENT_FACTORY_RESOURCE_ID = 0x0101057a;

    public static final String DEBUGGABLE_ATTRIBUTE_NAME = "debuggable";
    public static final String EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME = "extractNativeLibs";
    public static final String NAME_ATTRIBUTE_NAME = "name";
    public static final String APP_COMPONENT_FACTORY_ATTRIBUTE_NAME = "appComponentFactory";

    private static final Map<String, Integer> resourceIdMap = new HashMap<>() {{
        put(DEBUGGABLE_ATTRIBUTE_NAME, DEBUGGABLE_RESOURCE_ID);
        put(EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME, EXTRACT_NATIVE_LIBS_RESOURCE_ID);
        put(NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID);
        put(APP_COMPONENT_FACTORY_ATTRIBUTE_NAME, APP_COMPONENT_FACTORY_RESOURCE_ID);
    }};


    public static void putAttribute(String filePath, String outFileName, String elementName, String attributeName, String newValue)
            throws IOException {
        FileInputStream fileInputStream = new FileInputStream(filePath);
        Resources.XmlNode xmlNode = Resources.XmlNode.parseFrom(fileInputStream);
        Resources.XmlNode.Builder rootXmlNodeBuilder = Resources.XmlNode.newBuilder(xmlNode);
        Resources.XmlElement.Builder rootElementBuilder = rootXmlNodeBuilder.getElementBuilder();

        String namespaceUri = "";
        List<Resources.XmlNamespace> namespaceDeclarationList = rootElementBuilder.getNamespaceDeclarationList();
        for (Resources.XmlNamespace xmlNamespace : namespaceDeclarationList) {
            if("android".equals(xmlNamespace.getPrefix())) {
                namespaceUri = xmlNamespace.getUri();
            }
        }

        List<Resources.XmlAttribute.Builder> attributeList = rootElementBuilder.getAttributeBuilderList();

        boolean found = false;

        if(elementName.equals(rootElementBuilder.getName())) {
            for (Resources.XmlAttribute.Builder xmlAttribute : attributeList) {
                if(attributeName.equals(xmlAttribute.getName())) {
                    xmlAttribute.setValue(newValue);
                    found = true;
                }
            }
        }

        if(!found) {
            List<Resources.XmlNode.Builder> childBuilderList = rootElementBuilder.getChildBuilderList();
            for (int i = 0; i < childBuilderList.size(); i++) {
                Resources.XmlNode.Builder childBuilder = childBuilderList.get(i);

                /* IMPORTANT */
                if (childBuilder.hasElement()) {
                    Resources.XmlElement.Builder elementBuilder = childBuilder.getElementBuilder();

                    if (elementName.equals(elementBuilder.getName())) {
                        List<Resources.XmlAttribute.Builder> childAttributeList = elementBuilder.getAttributeBuilderList();
                        for (int j = 0; j < childAttributeList.size(); j++) {
                            Resources.XmlAttribute.Builder xmlAttribute = childAttributeList.get(j);
                            if (attributeName.equals(xmlAttribute.getName())) {
                                xmlAttribute.setValue(newValue);

                                found = true;
                            }
                        }

                        if (!found) {
                            Resources.XmlAttribute.Builder builder = Resources.XmlAttribute.newBuilder();
                            builder.setName(attributeName);
                            builder.setValue(newValue);
                            builder.setNamespaceUri(namespaceUri);

                            if("true".equals(newValue) || "false".equals(newValue)) {
                                Resources.Item.Builder itemBuilder = Resources.Item.newBuilder();
                                Resources.Primitive.Builder primitiveBuilder = Resources.Primitive.newBuilder();
                                primitiveBuilder.setBooleanValue(true);
                                itemBuilder.setPrim(primitiveBuilder);
                                builder.setCompiledItem(itemBuilder);
                            }

                            int resId = resourceIdMap.get(attributeName);
                            builder.setResourceId(resId);
                            elementBuilder.addAttribute(builder);
                        }
                    }

                }

            }
        }

        Resources.XmlNode build = rootXmlNodeBuilder.build();

        byte[] byteArray = build.toByteArray();
        FileOutputStream fos = new FileOutputStream(outFileName);

        fos.write(byteArray);
        IoUtils.close(fileInputStream);
        IoUtils.close(fos);
    }


    public static String getAttributeValue(String filePath, String elementName, String attributeName)
            throws IOException {
        FileInputStream fileInputStream = new FileInputStream(filePath);
        Resources.XmlNode xmlNode = Resources.XmlNode.parseFrom(fileInputStream);
        IoUtils.close(fileInputStream);
        Resources.XmlNode.Builder rootXmlNodeBuilder = Resources.XmlNode.newBuilder(xmlNode);
        Resources.XmlElement.Builder rootElementBuilder = rootXmlNodeBuilder.getElementBuilder();
        List<Resources.XmlAttribute.Builder> attributeList = rootElementBuilder.getAttributeBuilderList();

        if(elementName.equals(rootElementBuilder.getName())) {
            for (Resources.XmlAttribute.Builder xmlAttribute : attributeList) {
                if (attributeName.equals(xmlAttribute.getName())) {
                    return xmlAttribute.getValue();
                }
            }
        }

        List<Resources.XmlNode.Builder> childBuilderList = rootElementBuilder.getChildBuilderList();
        for (int i = 0; i < childBuilderList.size(); i++) {
            Resources.XmlNode.Builder nodeBuilder = childBuilderList.get(i);
            Resources.XmlElement.Builder elementBuilder = nodeBuilder.getElementBuilder();
            if(elementName.equals(elementBuilder.getName())) {
                List<Resources.XmlAttribute.Builder> childAttributeList = elementBuilder.getAttributeBuilderList();
                for (int j = 0; j < childAttributeList.size(); j++) {
                    Resources.XmlAttribute.Builder xmlAttribute = childAttributeList.get(j);
                    if (attributeName.equals(xmlAttribute.getName())) {
                        return xmlAttribute.getValue();
                    }
                }
            }
        }
        return null;
    }

}
