package com.luoye.dpt.util;

/**
 * @author luoyesiqiu
 */
public class TypeUtils {

    public static String getHumanizeTypeName(String name){
        if(name == null || "".equals(name)){
            return "";
        }
        switch (name){
            case "V":
                return "void";
            case "I":
                return "int";
            case "D":
                return "double";
            case "F":
                return "float";
            case "S":
                return "short";
            case "Z":
                return "boolean";
            case "J":
                return "long";
            case "B":
                return "byte";
            default:
                if(name.length() >= 2) {
                    return name.substring(1, name.length() - 1).replaceAll("/", ".");
                }
                else{
                    return name;
                }
        }
    }

}
