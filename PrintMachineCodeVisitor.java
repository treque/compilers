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
                MODIFIED.remove(ret);
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
            loadLine.add(left.replace("@", ""));

            MachLine loadMachLine = new MachLine(loadLine);
            LOADED.add(left.replace("@", ""));

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

        if (!LOADED.contains(left) && !left.contains("#")) // make sure we fill up LOADED somewhere. (at each getReg)
        {
            List<String> loadLine = new ArrayList<>();
            loadLine.add("LD");
            loadLine.add(left);
            loadLine.add(left.replace("@", "")); // left is supposed to be @ + something already. are we trying to remove the @..

            MachLine loadMachLine = new MachLine(loadLine);
            LOADED.add(left.replace("@", ""));

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

    public void compute_machineCode() {
        // TODO: Implement machine code with graph coloring for register assignation (REG is the register limitation)
        //       The pointers (ex: "@a") here should be replace by registers (ex: R0) respecting the coloring algorithm
        //       described in the TP requirements.

        Graph graph = new Graph();
        constructInteferenceGraph(graph);

        Graph savedGraph = new Graph();
        constructInteferenceGraph(savedGraph);

        Stack<String> stack = new Stack<String>();

        HashSet<String> spilled = new HashSet<>();
        while (!graph.adj.isEmpty())
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


            if (possibleEntries.isEmpty()) // spill
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
                String mostNeighborsEntry = set_ordered(possibleMostNeighbors).get(0);
                spilled.add(mostNeighborsEntry);
                ArrayList<Integer> uses = new ArrayList<Integer>();
                int extraLines = 0;

                for (int i = 0 ;  i < CODE.size() ; i++)
                {
                    if (CODE.get(i).line.contains(mostNeighborsEntry) && !CODE.get(i).line.get(0).equals("LD") && !CODE.get(i).line.get(0).equals("ST"))
                    {
                        uses.add(i);
                        break;
                    }
                }

                int first = uses.get(0);
                if (CODE.get(first).line.get(1).equals(mostNeighborsEntry) && !CODE.get(first).line.get(0).equals("ST") && !CODE.get(first).line.get(0).equals("LD"))
                {
                    List<String> instruction = new ArrayList<>();
                    instruction.add("ST");
                    // Strings are immutable so a copy is created instead. Here, we are trying to do ST a, @a
                    instruction.add(mostNeighborsEntry.replace("@", "").replace("!", "")); // p22 cours 12
                    instruction.add(mostNeighborsEntry);

                    MachLine storeLine = new MachLine(instruction);
                    CODE.add(uses.get(0) + 1, storeLine);
                    extraLines++;
                }

                uses.clear();
                if (CODE.get(first).Next_OUT.nextuse.get(mostNeighborsEntry) != null && !CODE.get(first).Next_OUT.nextuse.get(mostNeighborsEntry).isEmpty()) // one or the other would depend on how i implemented
                {
                    uses.addAll(CODE.get(first).Next_OUT.nextuse.get(mostNeighborsEntry)); // we need extraLines because this saves only the old values (next out isnt up to date anymore)
                    List<String> instruction = new ArrayList<>();
                    instruction.add("LD");
                    instruction.add(mostNeighborsEntry.concat("!"));
                    instruction.add(mostNeighborsEntry.replace("@", "").replace("!", "")); // LD @a!, a

                    MachLine loadLine = new MachLine(instruction);
                    CODE.add(uses.get(0) + extraLines, loadLine);

                    for (int i = uses.get(0) + extraLines; i < CODE.size(); ++i)
                    {
                        // now for the next occurences we concat a !
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

                compute_LifeVar(); // make sure it clears properly and iterates through the base code
                compute_NextUse(); // make sure it iterates through the whole code array and clears properly

                constructInteferenceGraph(graph);
                stack.clear();
            }
            else // no spill
            {
            String closestEntry = set_ordered(possibleEntries).get(0);
            graph.removeNode(closestEntry);
            stack.push(closestEntry);
            }

        }


        // COLORING
        HashSet<String> poppedNodes = new HashSet<>();
        HashMap<String, String> symbolicToReg = new HashMap<>();

        Graph coloredGraph = new Graph();
        while (!stack.isEmpty())
        {
            String poppedNode = stack.pop();

            HashSet<String> neighbors = savedGraph.adj.get(poppedNode);
            poppedNodes.add(poppedNode);

            coloredGraph.addNode(poppedNode);
            if (neighbors != null)
            {
                for (String pastPopped : poppedNodes) // adding adjacencies to the not-yet-colored-graph
                {
                    if (neighbors.contains(pastPopped))
                    {
                        coloredGraph.addAdj(pastPopped, poppedNode);
                    }
                }
            }

            // color the node
            if (coloredGraph.adj.get(poppedNode).isEmpty())
            {
                symbolicToReg.put(poppedNode, "R0");
            }
            else
            {
                // find the next available register
                int maxReg = 0;

                // first find the max
                for (String coloredNeighbor : coloredGraph.adj.get(poppedNode))
                {
                    int regNum = Integer.parseInt(symbolicToReg.get(coloredNeighbor).replace("R", ""));
                    if (regNum > maxReg)
                    {
                        maxReg = regNum;
                    }
                }
                // iterate through the neighbors to see if there is a gap
                // ex neighbours are 0 1 4, should add 2 and break at 2.
                HashSet<String> neighbours = coloredGraph.adj.get(poppedNode);
                HashSet<String> neighbourRegisters = new HashSet<>();

                // remplir le neighbourRegisters
                for (String neighbour : neighbours)
                {
                    String reg = symbolicToReg.get(neighbour);
                    neighbourRegisters.add(reg);
                }

                // now checking for the gap
                // ex on regarde si (0 1 4) contient 0, 1, 2, 3, 4
                int currentRegNumber = 0;
                while (currentRegNumber < maxReg)
                {
                    if (!neighbourRegisters.contains("R" + Integer.toString(currentRegNumber)))
                    {
                        // gap found
                        maxReg = currentRegNumber - 1; // -1 bc on fait +1 plus bas, on si je trouve que 2 est pas la, alors 1 + 1 = R2
                        break;
                    }
                    currentRegNumber++;
                }

                symbolicToReg.put(poppedNode, "R" + Integer.toString(maxReg + 1));
            }
        }

        for (int i = 0 ; i < CODE.size() ; i++) // updating the code
        {
            for (int j = 0 ; j < CODE.get(i).line.size() ; j++)
            {
                if (symbolicToReg.containsKey(CODE.get(i).line.get(j)))
                {
                    String symbolic = CODE.get(i).line.get(j);
                    CODE.get(i).line.set(j, symbolicToReg.get(symbolic));
                }
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
