package analyzer.visitors;

import analyzer.ast.*;
import com.sun.javafx.geom.Edge;
import com.sun.org.apache.bcel.internal.generic.ANEWARRAY;
import com.sun.org.apache.bcel.internal.generic.RET;
import com.sun.org.apache.bcel.internal.generic.RETURN;
import com.sun.org.apache.xpath.internal.operations.Bool;

import javax.crypto.Mac;
import java.awt.image.AreaAveragingScaleFilter;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.*;

public class PrintMachineCodeVisitor implements ParserVisitor {

    private PrintWriter m_writer = null;

    private Integer REG = 256; // default register limitation
    private ArrayList<String> RETURNED = new ArrayList<String>(); // returned variables from the return statement
    private ArrayList<MachLine> CODE   = new ArrayList<MachLine>(); // representation of the Machine Code in Machine lines (MachLine)
    private HashSet<String> LOADED   = new HashSet<String>(); // could be use to keep which variable/pointer are loaded/ defined while going through the intermediate code
    private HashSet<String> MODIFIED = new HashSet<String>(); // could be use to keep which variable/pointer are modified while going through the intermediate code

    private int STORES = 0;

    private HashMap<String,String> OP; // map to get the operation name from it's value
    public PrintMachineCodeVisitor(PrintWriter writer) {
        m_writer = writer;

        OP = new HashMap<>();
        OP.put("+", "ADD");
        OP.put("-", "MIN");
        OP.put("*", "MUL");
        OP.put("/", "DIV");


    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return null;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        // Visiter les enfants
        node.childrenAccept(this, null);

        compute_LifeVar(); // first Life variables computation (should be recalled when machine code generation)
        compute_NextUse(); // first Next-Use computation (should be recalled when machine code generation)
        compute_machineCode(); // generate the machine code from the CODE array (the CODE array should be transformed
        compute_LifeVar();
        compute_NextUse();
        for (int i = 0; i < CODE.size(); i++) // print the output
            m_writer.println(CODE.get(i));
        return null;
    }


    @Override
    public Object visit(ASTNumberRegister node, Object data) {
        REG = ((ASTIntValue) node.jjtGetChild(0)).getValue(); // get the limitation of register
        return null;
    }

    @Override
    public Object visit(ASTReturnStmt node, Object data) {
        for(int i = 0; i < node.jjtGetNumChildren(); i++) {
            String val = ((ASTIdentifier) node.jjtGetChild(i)).getValue();
            RETURNED.add("@" + val); // returned values (here are saved in "@*somthing*" format, you can change that if you want.

            // TODO: the returned variables should be added to the Life_OUT set of the last statement of the basic block (before the "ST" expressions in the machine code)
            CODE.get(CODE.size() - 1).Life_OUT.add("@" + val); // last last last statement adds all the returns
        }


        for (String ret : RETURNED)
        {
            if (MODIFIED.contains(ret)) // store if modified
            {
                List<String> line = new ArrayList<>();
                line.add("ST");
                line.add(ret.replace("@", ""));
                line.add(ret);

                MachLine machLine = new MachLine(line);
                // MODIFIED.remove(ret);
                machLine.Life_IN = (HashSet)CODE.get(CODE.size() - 1).Life_OUT.clone();
                machLine.Life_OUT = (HashSet)machLine.Life_IN.clone();
                machLine.Life_OUT.remove(ret);


                CODE.add(machLine);
                STORES++;
            }
        }

        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, null);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, null);
        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        // On ne visite pas les enfants puisque l'on va manuellement chercher leurs valeurs
        // On n'a rien a transférer aux enfants
        String assigned = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left     = (String) node.jjtGetChild(1).jjtAccept(this, null);
        String right    = (String) node.jjtGetChild(2).jjtAccept(this, null);
        String op       = node.getOp();

        // TODO: Modify CODE to add the needed MachLine.
        //       here the type of Assignment is "assigned = left op right" and you should put pointers in the MachLine at
        //       the moment (ex: "@a")

        if (!LOADED.contains(left) && !left.contains("#")) // make sure we fill up LOADED somewhere.
        {
            List<String> loadLine = new ArrayList<>();
            loadLine.add("LD");
            loadLine.add(left);
            loadLine.add(left.replace("@", ""));  // left is supposed to be @ + something already. are we trying to remove the @...

            MachLine loadMachLine = new MachLine(loadLine);
            LOADED.add(left);

            CODE.add(loadMachLine);
        }

        if (!LOADED.contains(right) && !right.contains("#")) // make sure we fill up LOADED somewhere.
        {
            List<String> loadLine = new ArrayList<>();
            loadLine.add("LD");
            loadLine.add(right);
            loadLine.add(right.replace("@", ""));

            MachLine loadMachLine = new MachLine(loadLine);
            LOADED.add(right);

            CODE.add(loadMachLine);
        }

        List<String> assignLine = new ArrayList<>();
        assignLine.add(OP.get(op));
        assignLine.add(assigned);
        assignLine.add(left);
        assignLine.add(right);

        MachLine assignMachLine = new MachLine(assignLine);

        CODE.add(assignMachLine);

        MODIFIED.add(assigned);
        LOADED.add(assigned); // not sure
        return null;
    }

    @Override
    public Object visit(ASTAssignUnaryStmt node, Object data) {
        // On ne visite pas les enfants puisque l'on va manuellement chercher leurs valeurs
        // On n'a rien a transférer aux enfants
        String assigned = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = (String) node.jjtGetChild(1).jjtAccept(this, null);

        // TODO: Modify CODE to add the needed MachLine.
        //       here the type of Assignment is "assigned = - left" and you should put pointers in the MachLine at
        //       the moment (ex: "@a")

        if (!LOADED.contains(left) && !left.contains("#")) // make sure we fill up LOADED somewher
        {
            List<String> loadLine = new ArrayList<>();
            loadLine.add("LD");
            loadLine.add(left);
            loadLine.add(left);

            MachLine loadMachLine = new MachLine(loadLine);
            LOADED.add(left);

            CODE.add(loadMachLine);
        }

        List<String> assignLine = new ArrayList<>();
        assignLine.add("SUB");
        assignLine.add(assigned);
        assignLine.add("#0");
        assignLine.add(left);

        MachLine assignMachLine = new MachLine(assignLine);

        CODE.add(assignMachLine);

        MODIFIED.add(assigned);
        LOADED.add(assigned); //not sure
        return null;
    }

    @Override
    public Object visit(ASTAssignDirectStmt node, Object data) {
        // On ne visite pas les enfants puisque l'on va manuellement chercher leurs valeurs
        // On n'a rien a transférer aux enfants
        String assigned = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = (String) node.jjtGetChild(1).jjtAccept(this, null);

        // TODO: Modify CODE to add the needed MachLine.
        //       here the type of Assignment is "assigned = left" and you should put pointers in the MachLine at
        //       the moment (ex: "@a")

        if (!LOADED.contains(left) && !left.contains("#"))
        {
            List<String> loadLine = new ArrayList<>();
            loadLine.add("LD");
            loadLine.add(left);
            loadLine.add(left);

            MachLine loadMachLine = new MachLine(loadLine);
            LOADED.add(left);
            CODE.add(loadMachLine);
        }

        List<String> assignLine = new ArrayList<>();
        assignLine.add("ADD");
        assignLine.add(assigned);
        assignLine.add("#0");
        assignLine.add(left);

        MachLine assignMachLine = new MachLine(assignLine);

        CODE.add(assignMachLine);

        MODIFIED.add(assigned);
        LOADED.add(assigned);
        return null;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        //nothing to do here
        return node.jjtGetChild(0).jjtAccept(this, null);
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        //nothing to do here
        return "#"+String.valueOf(node.getValue());
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        //nothing to do here
        return "@" + node.getValue();
    }


    private class NextUse {
        // NextUse class implementation: you can use it or redo it you're way
        public HashMap<String, ArrayList<Integer>> nextuse = new HashMap<String, ArrayList<Integer>>();

        public NextUse() {}

        public NextUse(HashMap<String, ArrayList<Integer>> nextuse) {
            this.nextuse = nextuse;
        }

        public void add(String s, int i) {
            if (!nextuse.containsKey(s)) {
                nextuse.put(s, new ArrayList<Integer>());
            }
            nextuse.get(s).add(i);
        }
        public String toString() {
            String buff = "";
            boolean first = true;
            for (String k : set_ordered(nextuse.keySet())) {
                if (! first) {
                    buff +=", ";
                }
                buff += k + ":" + nextuse.get(k);
                first = false;
            }
            return buff;
        }

        public Object clone() {
            return new NextUse((HashMap<String, ArrayList<Integer>>) nextuse.clone());
        }
    }


    private class MachLine {
        List<String> line;
        public HashSet<String> REF = new HashSet<String>();
        public HashSet<String> DEF = new HashSet<String>();
        public HashSet<Integer> SUCC  = new HashSet<Integer>();
        public HashSet<Integer> PRED  = new HashSet<Integer>();
        public HashSet<String> Life_IN  = new HashSet<String>();
        public HashSet<String> Life_OUT = new HashSet<String>();

        public NextUse Next_IN  = new NextUse();
        public NextUse Next_OUT = new NextUse();

        public MachLine(List<String> s) {
            this.line = s;
            int size = CODE.size();

            // PRED, SUCC, REF, DEF already computed (cadeau
            if (size > 0) {
                PRED.add(size-1);
                CODE.get(size-1).SUCC.add(size);
            }
            this.DEF.add(s.get(1));
            for (int i = 2; i < s.size(); i++)
                if (s.get(i).charAt(0) == '@')
                    this.REF.add(s.get(i));
        }

        public String toString() {
            String buff = "";

            // print line :
            buff += line.get(0) + " " + line.get(1);
            for (int i = 2; i < line.size(); i++)
                buff += ", " + line.get(i);
            buff +="\n";
            // you can uncomment the others set if you want to see them.
            //buff += "// REF      : " +  REF.toString() +"\n";
            // buff += "// DEF      : " +  DEF.toString() +"\n";
            // buff += "// PRED     : " +  PRED.toString() +"\n";
            // buff += "// SUCC     : " +  SUCC.toString() +"\n";
            // buff += "// MODIFIED     : " +  MODIFIED.toString() +"\n";
            // buff += "// RETURNED     : " +  RETURNED.toString() +"\n";
            buff += "// Life_IN  : " +  set_ordered(Life_IN).toString() +"\n";
            buff += "// Life_OUT : " +  set_ordered(Life_OUT).toString() +"\n";
            buff += "// Next_IN  : " +  Next_IN.toString() +"\n";
            buff += "// Next_OUT : " +  Next_OUT.toString() +"\n";
            return buff;
        }
    }

    private void compute_LifeVar() {
        // TODO: Implement LifeVariable algorithm on the CODE array (for machine code)

        for (int N = CODE.size() - STORES - 1 ; N >= 0; N--)
        {
            MachLine currentLine = CODE.get(N);

            currentLine.Life_OUT.clear();
            for (String node : CODE.get(N+1).Life_IN)
            {
                currentLine.Life_OUT.add(node);
            }
            currentLine.Life_IN.clear();
            currentLine.Life_IN = (HashSet)currentLine.Life_OUT.clone();
            for (String node : currentLine.DEF)
            {
                currentLine.Life_IN.remove(node);
            }
            for (String node : currentLine.REF)
            {
                currentLine.Life_IN.add(node);
            }

        }


    }

    private void compute_NextUse() {
        // TODO: Implement NextUse algorithm on the CODE array (for machine code)

        for (int N = CODE.size() - 1 ; N >= 0; N--)
        {
            MachLine currentLine = CODE.get(N);

            if (N == CODE.size() - 1)
            {
                currentLine.Next_OUT = new NextUse();

                currentLine.Next_IN = new NextUse();
                for (String ref : currentLine.REF)
                {
                    currentLine.Next_IN.add(ref, N);
                }
                for (String def : currentLine.DEF) // -def
                {
                    currentLine.Next_IN.nextuse.remove(def);
                }
            }
            else
            {

                currentLine.Next_OUT = (NextUse)CODE.get(N+1).Next_IN.clone();
                currentLine.Next_IN = (NextUse)currentLine.Next_OUT.clone();

                for (String ref : currentLine.REF) // +ref
                {
                    if (currentLine.Next_IN.nextuse.containsKey(ref))
                    {
                        ArrayList<Integer> uses = (ArrayList)CODE.get(N).Next_IN.nextuse.get(ref).clone();
                        uses.add(N);

                        currentLine.Next_IN.nextuse.put(ref, uses);
                    }
                    else
                    {
                        ArrayList<Integer> uses = new ArrayList<>();
                        uses.add(N);

                        currentLine.Next_IN.nextuse.put(ref, uses);
                    }

                }

                for (String def : currentLine.DEF) // -def
                {
                    currentLine.Next_IN.nextuse.remove(def); // maybe add a remove method thru NextUse
                }
            }

            //sorting the AL
            for (HashMap.Entry<String, ArrayList<Integer>> uses : currentLine.Next_IN.nextuse.entrySet())
            {
                ArrayList<Integer> sorted = (ArrayList<Integer>)uses.getValue().clone();
                Collections.sort(sorted);
                uses.setValue(sorted);
            }

        }
    }

    public String getNodeMostNeighbors(Graph graph, HashSet<String> spilled)
    {
        HashSet<String> possibleMostNeighbors = new HashSet();
        Integer maxNeighbors = Integer.MIN_VALUE;

        for (String node : graph.adj.keySet())
        {
            int nbNeighbours = graph.adj.get(node).size();
            if (nbNeighbours >= maxNeighbors && !spilled.contains(node))
            {
                if (nbNeighbours > maxNeighbors)
                {
                    maxNeighbors = nbNeighbours;
                    possibleMostNeighbors.clear();
                    possibleMostNeighbors.add(node);
                }
                else
                {
                    possibleMostNeighbors.add(node);
                }
            }
        }
        return set_ordered(possibleMostNeighbors).get(0);
    }

    public void spill(Graph graph, HashSet<String> spilled)
    {

        String mostNeighborsEntry = getNodeMostNeighbors(graph, spilled);
        spilled.add(mostNeighborsEntry);
        //**
        boolean spilledModified = false;
        //**
        ArrayList<Integer> uses = new ArrayList<Integer>();
        int extraLines = 0;

        int first = -1;
        for (int i = 0 ;  i < CODE.size() ; i++)
        {
            if (CODE.get(i).line.contains(mostNeighborsEntry) && !CODE.get(i).line.get(0).equals("LD") && !CODE.get(i).line.get(0).equals("ST"))
            {
                first = i;
                if (CODE.get(first).Next_OUT.nextuse.get(mostNeighborsEntry) != null)
                {
                    uses.addAll(CODE.get(first).Next_OUT.nextuse.get(mostNeighborsEntry));
                }
                break;
            }
        }

        if (CODE.get(first).DEF.contains(mostNeighborsEntry))
        {
            List<String> instruction = new ArrayList<>();
            instruction.add("ST");
            instruction.add(mostNeighborsEntry.replace("@", "").replace("!", "")); // p22 cours 12
            instruction.add(mostNeighborsEntry);

            MachLine storeLine = new MachLine(instruction);
            CODE.add(first + 1, storeLine);
            extraLines++;
            //**
            MODIFIED.remove(mostNeighborsEntry);
            //**
        }

        if (uses.isEmpty())
        {
            for (int i = first + extraLines + 1; i < CODE.size(); ++i)
            {
                ArrayList<String> currentLine = new ArrayList<String>(CODE.get(i).line);
                for (int j = 0; j < currentLine.size(); ++j)
                {
                    if (currentLine.get(j).equals(mostNeighborsEntry))
                    {
                        currentLine.set(j, mostNeighborsEntry.concat("!"));
                    }
                }
                MachLine newRegLine = new MachLine(currentLine);
                CODE.set(i, newRegLine);
            }
        }
        else
        {
            List<String> instruction = new ArrayList<>();
            instruction.add("LD");
            instruction.add(mostNeighborsEntry.concat("!"));
            instruction.add(mostNeighborsEntry.replace("@", "").replace("!", ""));

            MachLine loadLine = new MachLine(instruction);
            CODE.add(uses.get(0) + extraLines, loadLine);
            extraLines++;
            int codeSize = CODE.size();
            for (int i = uses.get(0) + extraLines; i < codeSize; ++i)
            {
                //**
                if (CODE.get(i).line.size() >= 3)
                {
                    if (CODE.get(i).line.get(0).equals("ST") && CODE.get(i).line.get(2).equals(mostNeighborsEntry))
                    {
                        CODE.remove(i);
                        extraLines--;
                        codeSize--;
                    }
                }
                //**
                ArrayList<String> currentLine = new ArrayList<String>(CODE.get(i).line);
                for (int j = 0; j < currentLine.size(); ++j)
                {
                    if (currentLine.get(j).equals(mostNeighborsEntry))
                    {
                        currentLine.set(j, mostNeighborsEntry.concat("!"));
                    }
                }
                MachLine newRegLine = new MachLine(currentLine);
                CODE.set(i, newRegLine);
            }
        }

        // ***
        for (int i = first + 1; i < CODE.size(); ++i)
        {
            if (CODE.get(i).DEF.contains(mostNeighborsEntry.concat("!")))
            {
                spilledModified = true;
            }
        }

        if (spilledModified)
        {
            List<String> instruction = new ArrayList<>();
            instruction.add("ST");
            instruction.add(mostNeighborsEntry.replace("@", "").replace("!", ""));
            instruction.add(mostNeighborsEntry.concat("!"));

            MachLine loadLine = new MachLine(instruction);
            CODE.add(loadLine);
        }
        //**
    }

    public HashSet<String> getPossibleEntriesUnderK(Graph graph)
    {
        Integer kDiff = Integer.MAX_VALUE;
        HashSet<String> possibleEntries = new HashSet();

        // finding the nearest node
        for (String node : graph.adj.keySet())
        {
            int nbNeighbours = graph.adj.get(node).size();
            if (nbNeighbours < REG)
            {
                if (REG - nbNeighbours <= kDiff)
                {
                    if (REG - nbNeighbours < kDiff)
                    {
                        kDiff = REG - nbNeighbours;
                        possibleEntries.clear();
                        possibleEntries.add(node);
                    }
                    else
                    {
                        possibleEntries.add(node);
                    }
                }
            }
        }

        return possibleEntries;
    }


    public void compute_machineCode() {
        Graph graph = new Graph();
        constructInteferenceGraph(graph);

        Graph savedGraph = new Graph();
        constructInteferenceGraph(savedGraph);

        Stack<String> stack = new Stack<String>();

        HashSet<String> spilled = new HashSet<>();
        while (!graph.adj.isEmpty())
        {
            HashSet<String> possibleEntries = getPossibleEntriesUnderK(graph);

            if (possibleEntries.isEmpty()) // spill
            {
                spill(graph, spilled);
                compute_LifeVar();
                compute_NextUse();
                constructInteferenceGraph(graph);
                constructInteferenceGraph(savedGraph);
                stack.clear();
            }
            else // no spill
            {
                String closestEntry = set_ordered(possibleEntries).get(0);
                stack.push(closestEntry);
                graph.removeNode(closestEntry);
            }

        }

        color(savedGraph, stack);
        optimize(); // no need to recalculate next_use after
    }

    public void optimize()
    {
        for (int i = 0; i < CODE.size(); ++i)
        {
            List<String> currentLine = CODE.get(i).line;
            if (currentLine.get(0).equals("ADD") && currentLine.get(2).equals("#0") && currentLine.get(1).equals(currentLine.get(3))) // add r0 #0 r0
            {
                CODE.remove(i);
            }
            // maybe commutatitvite de laddition
        }
    }

    public void color(Graph savedGraph, Stack<String> stack)
    {
        HashMap<String, Integer> varToRegister = new HashMap<>();
        while (!stack.isEmpty()) {
            String variable = stack.pop();
            Integer no = 0;
            boolean foundRegNo = false;
            boolean incrementedRegNo = false;
            while (!foundRegNo) {
                for (String neighbor : savedGraph.adj.get(variable)) {
                    if (no.equals(varToRegister.get(neighbor))) {
                        no++;
                        incrementedRegNo = true;
                    }
                }
                if (!incrementedRegNo) foundRegNo = true;
                incrementedRegNo = false;
            }
            varToRegister.put(variable, no);
        }

        for (MachLine machLine : CODE) {
            for (int i = 0; i < machLine.line.size(); i++) {
                if (machLine.line.get(i).contains("@")) machLine.line.set(i, "R" + varToRegister.get(machLine.line.get(i)));
            }
        }

    }

    public List<String> set_ordered(Set<String> s) {
        // function given to order a set in alphabetic order TODO: use it! or redo-it yourself
        List<String> list = new ArrayList<String>(s);
        Collections.sort(list);
        return list;
    }

    // TODO: add any class you judge necessary, and explain them in the report. GOOD LUCK!
    public class Graph
    {
        public HashMap<String, HashSet<String>> adj; // liste d'adjacence

        Graph()
        {
            adj = new HashMap<String, HashSet<String>>();
        }

        void addAdj(String v, String w)
        {
            if (!adj.containsKey(v)) {
                adj.put(v, new HashSet<String>());
            }
            adj.get(v).add(w);

            if (!adj.containsKey(w)) {
                adj.put(w, new HashSet<String>());
            }
            adj.get(w).add(v);
        }

        void addNode(String v)
        {
            if (!adj.containsKey(v)) {
                adj.put(v, new HashSet<String>());
            }
        }

        void removeNode(String v)
        {
            adj.remove(v);
            for (String node : adj.keySet())
            {
                if (adj.get(node).contains(v))
                {
                    adj.get(node).remove(v); // this is supposed to give me a reference to the set of neighbors
                }
            }

        }

    }

    void constructInteferenceGraph(Graph graph)
    {
        graph.adj.clear();
        for (MachLine line : CODE)
        {
            for (String v : line.Next_OUT.nextuse.keySet())
            {
                for (String w : line.Next_OUT.nextuse.keySet())
                {
                    graph.addAdj(v, w);
                }
            }
        }

        for (HashMap.Entry<String, HashSet<String>> entry : graph.adj.entrySet())
        {
            graph.adj.get(entry.getKey()).remove(entry.getKey()); // remove the key from the neighbors plz lord
        }

    }

}