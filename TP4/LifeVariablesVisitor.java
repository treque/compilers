package analyzer.visitors;

import analyzer.ast.*;
import com.sun.org.apache.xpath.internal.operations.Bool;
import sun.tools.java.Identifier;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Predicate;


/**
 * Created: 19-02-15
 * Last Changed: 19-11-14
 * Author: Félix Brunet & Doriane Olewicki
 *
 * Description: Ce visiteur explore l'AST et génère un code intermédiaire.
 */

public class LifeVariablesVisitor implements ParserVisitor {

    //le m_writer est un Output_Stream connecter au fichier "result". c'est donc ce qui permet de print dans les fichiers
    //le code généré.
    private /*final*/ PrintWriter m_writer;

    public LifeVariablesVisitor(PrintWriter writer) {
        m_writer = writer;
    }
    public HashMap<String, StepStatus> allSteps = new HashMap<>();

    private int step = 0;
    private HashSet<String> previous_step = new HashSet<>();

    /*
    génère une nouvelle variable temporaire qu'il est possible de print
    À noté qu'il serait possible de rentrer en conflit avec un nom de variable définit dans le programme.
    Par simplicité, dans ce tp, nous ne concidérerons pas cette possibilité, mais il faudrait un générateur de nom de
    variable beaucoup plus robuste dans un vrai compilateur.
     */
    private String genStep() {
        return "_step" + step++;
    }



    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data)  {
        //HashSet<String> previous_step = new HashSet<>();
        node.childrenAccept(this, data);
        compute_IN_OUT();
        for (int i = 0; i < step; i++) {
            m_writer.write("===== STEP " + i + " ===== \n" + allSteps.get("_step" + i).toString());
        }
        return null;
    }

    @Override
    public Object visit(ASTDeclaration node, Object data) {
        ArrayList<String> decs= new ArrayList<>();

        node.childrenAccept(this, decs);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {

        String step = genStep();
        StepStatus s = new StepStatus();
        allSteps.put(step, s);

        for (String e : previous_step) {
            allSteps.get(step).PRED.add(e);
            allSteps.get(e).SUCC.add(step);
        }


        // Update previous step to current
        previous_step = new HashSet<>();
        previous_step.add(step);

        node.childrenAccept(this, previous_step);
        return previous_step;
    }

    /*
    le If Stmt doit vérifier s'il à trois enfants pour savoir s'il s'agit d'un "if-then" ou d'un "if-then-else".
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        ArrayList<String> exp = new ArrayList<>();
        // exp
        node.jjtGetChild(0).jjtAccept(this, exp);
        for (String step : previous_step) // le if(exp)
        {
            for (String ref : exp)
            {
                allSteps.get(step).REF.add(ref); // ajoute les ref lorsques trouves
            }
        }

        HashSet<String> mem_previous_step = (HashSet<String>) previous_step.clone();
        HashSet<String> if_previous_step;

        if (node.jjtGetNumChildren() == 2) {
            if_previous_step = (HashSet<String>) previous_step.clone(); // if no else
        }
        else {
            if_previous_step = new HashSet<>(); // if there is a else
        }
        for(int i=1; i < node.jjtGetNumChildren(); i++ ){
            //reset previous for each block
            previous_step = (HashSet<String>) mem_previous_step.clone();
            node.jjtGetChild(i).jjtAccept(this,data);
            for (String e : previous_step) {
                if_previous_step.add(e);
            }
        }

        previous_step = (HashSet<String>) if_previous_step.clone();
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {

        HashSet<String> mem_previous_step = (HashSet<String>)previous_step.clone();

        ArrayList<String> exp = new ArrayList<>();
        // get all exp
        node.jjtGetChild(0).jjtAccept(this, exp);
        for(String step : previous_step)
        {
            for (String ref : exp)
            {
                allSteps.get(step).REF.add(ref);
            }
        }

        // visit the block
        node.jjtGetChild(1).jjtAccept(this, data);

        for(String prev_step : mem_previous_step)
        {
            for (String last : previous_step)
            {
                allSteps.get(last).SUCC.add(prev_step);
                allSteps.get(prev_step).PRED.add(last);
            }
        }
        previous_step = mem_previous_step;
        return null;
    }


    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        for (String step: previous_step)
        {
            // dis the def
            ArrayList<String> id = new ArrayList<String>();
            node.jjtGetChild(0).jjtAccept(this, id);
            // dis the value
            ArrayList<String> refs = new ArrayList<String>();
            node.jjtGetChild(1).jjtAccept(this, refs);

            for (String def: id)
            {
                allSteps.get(step).DEF.add(def);
            }
            for (String ref: refs)
            {
                allSteps.get(step).REF.add(ref);
            }
        }
        return null;
    }


    //Il n'y a probablement rien à faire ici
    @Override
    public Object visit(ASTExpr node, Object data){
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTNotExpr node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTGenValue node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTBoolValue node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        if (data instanceof ArrayList)
        {
            // add the id to the refs or defs here
            ((ArrayList<String>)data).add(node.getValue());
        }
        return null;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }



    @Override
    public Object visit(ASTSwitchStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTDefaultStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    //utile surtout pour envoyé de l'informations au enfant des expressions logiques.
    private class StepStatus {
        public HashSet<String> REF = new HashSet<String>();
        public HashSet<String> DEF = new HashSet<String>();
        public HashSet<String> IN  = new HashSet<String>();
        public HashSet<String> OUT = new HashSet<String>();

        public HashSet<String> SUCC  = new HashSet<String>();
        public HashSet<String> PRED  = new HashSet<String>();

        public String toString() {
            String buff = "";
            buff += "REF : " + set_ordered(REF) +"\n";
            buff += "DEF : " + set_ordered(DEF) +"\n";
            buff += "IN  : " + set_ordered(IN) +"\n";
            buff += "OUT : " + set_ordered(OUT) +"\n";

            buff += "SUCC: " + set_ordered(SUCC) +"\n";
            buff += "PRED: " + set_ordered(PRED) +"\n";
            buff += "\n";
            return buff;
        }

        public String set_ordered(HashSet<String> s) {
            List<String> list = new ArrayList<String>(s);
            Collections.sort(list);

            return list.toString();
        }
    }

    private void compute_IN_OUT() {
        // just loop through all the steps
       for (String step: previous_step)
       {
           LinkedList<String> stack = new LinkedList<>(); // linkedlists better act as queues
           stack.push(step);

           while(!stack.isEmpty()) // should be empty once it stops pushing preds
           {
               String currentStep = stack.pop();
               HashSet<String> oldIN = (HashSet<String>)allSteps.get(currentStep).IN.clone(); // to avoid loops later

               for (String succ : allSteps.get(currentStep).SUCC) // for every step
               {
                   for (String out : allSteps.get(succ).IN) // for out, for all succ in step
                   {
                       allSteps.get(currentStep).OUT.add(out); //out(i) = out(i) U in(succ)
                   }
               }

               // in(i) = ref(i) U (out(i) - def(i)
               allSteps.get(currentStep).IN = (HashSet<String>)allSteps.get(currentStep).OUT.clone(); // out(i)
               for (String def : allSteps.get(currentStep).DEF)
               {
                   allSteps.get(currentStep).IN.remove(def); // -def(i)
               }
               for (String ref : allSteps.get(currentStep).REF)
               {
                   allSteps.get(currentStep).IN.add(ref); // ref(i)
               }

               if (!allSteps.get(currentStep).IN.equals(oldIN)) // avoiding loops
               {
                    for (String pred : allSteps.get(currentStep).PRED)
                    {
                        stack.push(pred);
                    }
               }
           }
       }
    }
}
