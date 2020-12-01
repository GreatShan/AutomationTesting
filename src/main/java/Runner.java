import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.WalaException;

import java.io.IOException;
import java.util.Set;
import java.io.*;
import java.util.*;

public class Runner {
    // 项目的入口
    private static TestProcedure procedure = new TestProcedure();

    public static void main(String[] args) throws Exception {
        // 输入格式：java -jar testSelection.jar -c/-m <project_target> <change_info>
        // <project_target>指向待测项目的target文件夹；<change_info>指向记录了变更信息的文本文件
        makeDots();

        String type = args[0];
        String project_target = args[1];
        String change_info = args[2];

        // 获取命令行输入的参数
        if (args.length < 3 && args[0].length() != 2) {
            System.out.println("参数格式错误");
            System.exit(-1);
        }

        CHACallGraph callGraph = null;
        CallGraph graph = null;

        AnalysisScope scope = procedure.makeAnalysisScope(project_target);

        // 生成类层次关系对象
        ClassHierarchy hierarchy = ClassHierarchyFactory.makeWithRoot(scope);

        Set<Entrypoint> entryPoints = new AllApplicationEntrypoints(scope, hierarchy);
        callGraph = procedure.makeCHACallGraph(hierarchy, entryPoints);
        graph = procedure.make0CFACallGraph(scope);

        switch (args[0].charAt(1)) {
            case 'c': {
                try {
                    chooseClassTestCase(project_target, change_info, callGraph);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InvalidClassFileException e) {
                    e.printStackTrace();
                }
                break;
            }
            case 'm': {
                try {
                    chooseMethodTestCase(project_target, change_info, graph);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InvalidClassFileException e) {
                    e.printStackTrace();
                }
                break;
            }
            default: {
                System.out.println("参数格式错误");
                System.exit(-1);
            }

        }

    }

    private static void chooseClassTestCase(String projectTarget, String changeInfo, CHACallGraph callGraph)
            throws IOException, InvalidClassFileException {
        procedure.testOnClassLevel(changeInfo, callGraph, "./selection-class.txt");
    }

    private static void chooseMethodTestCase(String projectTarget, String changeInfo, CallGraph graph)
            throws IOException, InvalidClassFileException {
        procedure.testOnMethodLevel(changeInfo, graph, "./selection-method.txt");
    }

    public static void makeDots() {

        String[] paths = new String[] { "data/1-ALU/target", "data/2-DataLog/target", "data/3-BinaryHeap/target"
                , "data/4-NextDay/target", "data/5-MoreTriangle/target" };
        String[] names = new String[] { "ALU", "DataLog", "BinaryHeap", "NextDay", "MoreTriangle" };

        for (int i = 0; i < paths.length; i += 1) {
            try {
                AnalysisScope scope = procedure.makeAnalysisScope(paths[i]);
                ClassHierarchy hierarchy = ClassHierarchyFactory.makeWithRoot(scope);

                Set<Entrypoint> entryPoints = new AllApplicationEntrypoints(scope, hierarchy);
                CHACallGraph callGraph = procedure.makeCHACallGraph(hierarchy, entryPoints);
                CallGraph graph = procedure.make0CFACallGraph(scope);
                procedure.analyzeCHACallGraphToCDG(callGraph, names[i]);
                procedure.analyzeCallGraphToMDG(graph, names[i]);

            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassHierarchyException e) {
                e.printStackTrace();
            } catch (WalaException e) {
                e.printStackTrace();
            } catch (CancelException e) {
                e.printStackTrace();
            } catch (InvalidClassFileException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
