import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.WalaException;

import java.io.*;
import java.util.*;

public class Tools {
    /*
     * 生成.dot文件
     */
    public static void makeDot(Hashtable<String, Set<String>> table, String projectName, boolean isClass)
            throws IOException {
        String path = "src/report";
        File f = new File(path);
        if (!f.exists())
            f.mkdir();
        path = path.concat("/" + String.format("%s-%s.dot", isClass ? "class" : "method", projectName));
        f = new File(path);
        if (f.exists()) {
            f.delete();
            f.createNewFile();
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(f));
        writer.append(String.format("digraph %s_%s {\n", projectName, isClass ? "class" : "method"));
        for (String k : table.keySet()) {
            for (String s : table.get(k)) {
                writer.append(String.format("    \"%s\"->\"%s\";\n", s, k));
                writer.flush();
            }
        }
        writer.append("}");
        writer.flush();
        writer.close();
    }


    public static List<String> readChangeInfo(File file) throws IOException {
        List<String> list = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String c;
        while ((c = reader.readLine()) != null) {
            list.add(c.trim().split(" ")[1]);
        }
        return list;
    }

    // 获取文件
    public static ArrayList<File> getFiles(String target) {
        File file = new File(target);
        String[] paths = file.list();
        ArrayList<File> result = new ArrayList<File>();

        if (paths == null) {
            return null;
        }

        for (String path : paths) {
            File tempFile = new File(target + "/" + path);
            // 是文件夹，递归
            if (tempFile.isDirectory()) {
                ArrayList<File> temp = getFiles(target + "/" + path);
                if (temp != null) {
                    result.addAll(temp);
                }
            } else {
                // 取得文件
                if (path.endsWith(".class")) {
                    result.add(tempFile);
                }
            }
        }
        return result;
    }

}
