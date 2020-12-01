import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class TestProcedure {

    // 生成分析域
    public AnalysisScope makeAnalysisScope(String project_target) throws Exception {
        AnalysisScope scope;
        ArrayList<File> classes;
        ArrayList<File> testClasses;

        //统一格式，去除末尾的"/"防止出错
        if (project_target.endsWith("/")) {
            project_target = project_target.substring(0, project_target.length() - 1);
        }

        //scope包含classes和test-classes两个文件夹里的class文件
        scope = AnalysisScopeReader.readJavaScope("scope.txt", new File("exclusion.txt"), AnalysisScope.class.getClassLoader());
        classes = Tools.getFiles(project_target + "/classes");
        assert classes != null;
        for (File file : classes) {
            scope.addClassFileToScope(ClassLoaderReference.Application, file);
        }
        testClasses = Tools.getFiles(project_target + "/test-classes");
        assert testClasses != null;
        for (File file : testClasses) {
            scope.addClassFileToScope(ClassLoaderReference.Application, file);
        }
        return scope;
    }


    public CHACallGraph makeCHACallGraph(ClassHierarchy hierarchy, Iterable<Entrypoint> entryPoints)
            throws CancelException {
        CHACallGraph graph = new CHACallGraph(hierarchy);
        graph.init(entryPoints);
        return graph;
    }

    public CallGraph make0CFACallGraph(AnalysisScope scope) throws ClassHierarchyException, CancelException {
        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);
        AllApplicationEntrypoints entryPoints = new AllApplicationEntrypoints(scope, cha);
        AnalysisOptions option = new AnalysisOptions(scope, entryPoints);
        SSAPropagationCallGraphBuilder builder = Util.makeZeroCFABuilder(Language.JAVA, option, new AnalysisCacheImpl(),
                cha, scope);
        return builder.makeCallGraph(option);
    }

    private Hashtable<String, Set<String>> analyzeCHACallGraphToTable(CHACallGraph callGraph)
            throws InvalidClassFileException {
        Hashtable<String, Set<String>> hashtable = new Hashtable<String, Set<String>>();
        HashSet<String> customClasses = new HashSet<String>();

        for (CGNode node : callGraph) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    customClasses.add(method.getDeclaringClass().getName().toString());
                }
            }
        }

        // README里的方法
        for (CGNode node : callGraph) {
            // node中包含了很多信息，包括类加载器、方法信息等，这里只筛选出需要的信息
            if (node.getMethod() instanceof ShrikeBTMethod) {
                // node.getMethod()返回一个比较泛化的IMethod实例，不能获取到我们想要的信息
                // 一般地，本项目中所有和业务逻辑相关的方法都是ShrikeBTMethod对象
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    // 获取声明该方法的类的内部表示
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    // 获取方法签名
                    if (!hashtable.containsKey(classInnerName)) {
                        hashtable.put(classInnerName, new HashSet<String>());

                    }

                    // 获取方法的调用者
                    for (CallSiteReference reference : method.getCallSites()) {
                        // 进行过滤去掉java内置类
                        if (customClasses
                                .contains(reference.getDeclaredTarget().getDeclaringClass().getName().toString())) {
                            hashtable.get(classInnerName)
                                    .add(reference.getDeclaredTarget().getDeclaringClass().getName().toString());
                        }
                    }

                }
            }
        }
        return hashtable;
    }

    /*
     * CHACallGraph转类依赖图
     */
    public void analyzeCHACallGraphToCDG(CHACallGraph callGraph, String projectName)
            throws InvalidClassFileException, IOException {
        Hashtable<String, Set<String>> hashtable = analyzeCHACallGraphToTable(callGraph);
        Tools.makeDot(hashtable, projectName, true);
    }

    private Hashtable<String, Set<String>> analyzeCallGraphToTable(CallGraph callGraph)
            throws InvalidClassFileException {
        Hashtable<String, Set<String>> hashtable = new Hashtable<String, Set<String>>();
        String prefix = null;
        for (CGNode node : callGraph) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    // 获得命名空间
                    if (prefix == null) {
                        prefix = method.getDeclaringClass().getName().toString().substring(1);
                        String[] s = prefix.split("/");
                        if (s.length <= 2) {
                            System.out.println("命名空间错误");
                        }
                        prefix = s[0] + "." + s[1];
                    }
                    // 需要进行分析的方法
                    String signature = method.getSignature();
                    if (!hashtable.containsKey(signature)) {
                        hashtable.put(signature, new HashSet<String>());
                    }

                    // 获取方法的调用者
                    for (CallSiteReference reference : method.getCallSites()) {
                        // 过滤
                        Pattern pattern = Pattern.compile("[^$]+\\(.*\\).*");
                        if (pattern.matcher(reference.getDeclaredTarget().getSignature()).matches()
                                && reference.getDeclaredTarget().getSignature().startsWith(prefix)) {
                            hashtable.get(signature).add(reference.getDeclaredTarget().getSignature());
                        }

                    }

                }
            }
        }
        return hashtable;
    }

    /*
     * CallGraph转方法依赖图
     */
    public void analyzeCallGraphToMDG(CallGraph callGraph, String projectName)
            throws InvalidClassFileException, IOException {
        Hashtable<String, Set<String>> hashtable = analyzeCallGraphToTable(callGraph);
        Tools.makeDot(hashtable, projectName, false);
    }

    /*
     * 变更方法选择类级测试方法
     */
    public void testOnClassLevel(String changeInfo, CHACallGraph callGraph, String dest)
            throws IOException, InvalidClassFileException {
        File file = new File(changeInfo);
        List<String> changedMethodSignatures = Tools.readChangeInfo(file);
        Hashtable<String, Set<String>> hashtable = calClosure(analyzeCHACallGraphToTable(callGraph));
        Set<String> classesNeedToChange = new HashSet<String>();
        Set<String> results = new HashSet<String>();

        for (CGNode node : callGraph) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    if (changedMethodSignatures.contains(method.getSignature())) {
                        // 是变更的方法
                        // classesNeedToChange.add(method.getDeclaringClass().getName().toString());
                        for (String k : hashtable.keySet()) {
                            for (String v : hashtable.get(k)) {
                                if (v.equals(method.getDeclaringClass().getName().toString())) {
                                    classesNeedToChange.add(k);
                                }
                            }
                        }
                    }
                }
            }
        }

        for (CGNode node : callGraph) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if (classesNeedToChange.contains(method.getDeclaringClass().getName().toString()) && isTest(method)) {
                    results.add(method.getDeclaringClass().getName().toString() + " " + method.getSignature());
                }
            }

        }


        File f = new File(dest);
        if (f.exists()) {
            f.delete();
            f.createNewFile();
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(f));
        for (String s : results) {
            writer.append(s + "\n");
            writer.flush();
        }
        writer.close();
    }

    /*
     * 变更方法选择方法级测试方法
     */
    public void testOnMethodLevel(String changeInfo, CallGraph callGraph, String dest)
            throws IOException, InvalidClassFileException {
        File file = new File(changeInfo);
        List<String> changedMethodSignatures = Tools.readChangeInfo(file);
        Hashtable<String, Set<String>> hashtable = calClosure(analyzeCallGraphToTable(callGraph));
        Set<String> methodsNeedToChange = new HashSet<String>();
        Set<String> results = new HashSet<String>();

        for (CGNode node : callGraph) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    if (changedMethodSignatures.contains(method.getSignature())) {
                        // 是变更的方法
                        for (String k : hashtable.keySet()) {
                            for (String v : hashtable.get(k)) {
                                if (v.equals(method.getSignature())) {
                                    methodsNeedToChange.add(k);
                                }
                            }
                        }
                    }
                }
            }
        }
        for (CGNode node : callGraph) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if (methodsNeedToChange.contains(method.getSignature()) && isTest(method)) {
                    results.add(method.getDeclaringClass().getName().toString() + " " + method.getSignature());
                }
            }
        }

        File f = new File(dest);
        if (f.exists()) {
            f.delete();
            f.createNewFile();
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(f));
        for (String s : results) {
            writer.append(s + "\n");
            writer.flush();
        }
        writer.close();
    }

    /*
     * 判断方法是否为测试方法 todo
     */
    private static boolean isTest(ShrikeBTMethod method) {
        Collection<Annotation> annotations = method.getAnnotations();
        for (Annotation annotation : annotations) {
            if (new String(annotation.getType().getName().getClassName().getValArray()).equals("Test")) {
                return true;
            }
        }
        return false;
    }

    private static Hashtable<String, Set<String>> calClosure(Hashtable<String, Set<String>> hashtable) {
        // 对标签进行标号
        Hashtable<String, Set<String>> results = hashtable;
        List<String> tags = new ArrayList<String>();
        for (String k : hashtable.keySet()) {
            if (!tags.contains(k)) {
                tags.add(k);
            }
            for (String v : hashtable.get(k)) {
                if (!tags.contains(v)) {
                    tags.add(v);
                }
            }
        }

        // 建立矩阵,默认初始化为0
        int[][] m = new int[tags.size()][tags.size()];
        for (String k : hashtable.keySet()) {
            for (String v : hashtable.get(k)) {
                m[tags.indexOf(k)][tags.indexOf(v)] = 1;
            }
        }

        // 费舍尔算法
        for (int i = 0; i < m.length; i += 1) {
            // 对每一个0位检查
            for (int j = 0; j < m.length; j += 1) {
                for (int k = 0; k < m.length; k += 1) {
                    if (m[j][k] == 0) {
                        for (int q = 0; q < m.length; q += 1) {
                            if (m[j][q] == 1 && m[q][k] == 1) {
                                m[j][k] = 1;
                                break;
                            }
                        }
                    }
                }
            }
        }

        for (String k : results.keySet()) {
            for (int i = 0; i < m.length; i += 1) {
                if (m[tags.indexOf(k)][i] == 1) {
                    results.get(k).add(tags.get(i));
                }
            }
        }
        return results;

    }

}
