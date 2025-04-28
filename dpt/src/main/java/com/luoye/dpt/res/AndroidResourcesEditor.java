package com.luoye.dpt.res;

import com.android.aapt.Resources;
import com.luoye.dpt.util.IoUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author luoyesiqiu
 */
public class AndroidResourcesEditor {

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
