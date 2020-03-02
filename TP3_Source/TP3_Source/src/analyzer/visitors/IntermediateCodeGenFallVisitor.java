package analyzer.visitors;

import analyzer.ast.*;
import com.sun.org.apache.xpath.internal.operations.Bool;
//import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import sun.awt.Symbol;

import java.awt.*;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;


/**
 * Created: 19-02-15
 * Last Changed: 19-10-20
 * Author: Félix Brunet & Doriane Olewicki
 *
 * Description: Ce visiteur explore l'AST et génère un code intermédiaire.
 */

public class IntermediateCodeGenFallVisitor implements ParserVisitor {

    //le m_writer est un Output_Stream connecter au fichier "result". c'est donc ce qui permet de print dans les fichiers
    //le code généré.
    private final PrintWriter m_writer;

    public IntermediateCodeGenFallVisitor(PrintWriter writer) {
        m_writer = writer;
    }

    public HashMap<String, VarType> SymbolTable = new HashMap<>();

    private int id = 0;
    private int label = 0;

    /*
    génère une nouvelle variable temporaire qu'il est possible de print
    À noté qu'il serait possible de rentrer en conflit avec un nom de variable définit dans le programme.
    Par simplicité, dans ce tp, nous ne concidérerons pas cette possibilité, mais il faudrait un générateur de nom de
    variable beaucoup plus robuste dans un vrai compilateur.
     */
    private String genId() {
        return "_t" + id++;
    }

    //génère un nouveau Label qu'il est possible de print.
    private String genLabel() {
        return "_L" + label++;
    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    /*
    Code fournis pour remplir la table de symbole.
    Les déclarations ne sont plus utile dans le code à trois adresse.
    elle ne sont donc pas concervé.
     */
    @Override
    public Object visit(ASTDeclaration node, Object data) {
        ASTIdentifier id = (ASTIdentifier) node.jjtGetChild(0);
        VarType type;
        if (node.getValue().equals("bool")) {
            type = VarType.Bool;
        } else {
            type = VarType.Number;
        }
        SymbolTable.put(id.getValue(), type);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    /*
    le If Stmt doit vérifier s'il à trois enfants pour savoir s'il s'agit d'un "if-then" ou d'un "if-then-else".
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {

        String Snext = genLabel();

        int nbChildren = node.jjtGetNumChildren();

        if(nbChildren == 2) // if then
        {
            // ltrue, lfalse, id
            BoolLabel B = new BoolLabel("fall", Snext, null);
            node.jjtGetChild(0).jjtAccept(this, B);

            B = new BoolLabel("fall", Snext, null);
            node.jjtGetChild(1).jjtAccept(this, B );
        }
        else if (nbChildren == 3) // if then else
        {
            BoolLabel B = new BoolLabel("fall", genLabel(), null);
            node.jjtGetChild(0).jjtAccept(this, B);
            node.jjtGetChild(1).jjtAccept(this, B);
            m_writer.println("goto " + Snext);
            m_writer.println(B.lFalse);
            node.jjtGetChild(2).jjtAccept(this, B);
        }

        m_writer.println(Snext);
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        String Snext = genLabel();
        String begin = genLabel();

        BoolLabel B = new BoolLabel("fall", Snext, null);

        m_writer.println(begin); // label(begin)

        node.jjtGetChild(0).jjtAccept(this, B); // visit(B)

        node.jjtGetChild(1).jjtAccept(this, B); // visit(S1)

        m_writer.print("goto " + begin);

        m_writer.println(Snext);
        return null;
    }

    /*
     *  la difficulté est d'implémenter le "short-circuit" des opérations logiques combinez à l'enregistrement des
     *  valeurs booléennes dans des variables.
     *
     *  par exemple,
     *  a = b || c && !d
     *  deviens
     *  if(b)
     *      t1 = 1
     *  else if(c)
     *      if(d)
     *         t1 = 1
     *      else
     *         t1 = 0
     *  else
     *      t1 = 0
     *  a = t1
     *
     *  qui est équivalent à :
     *
     *  if b goto LTrue
     *  ifFalse c goto LFalse
     *  ifFalse d goto LTrue
     *  goto LFalse
     *  //Assign
     *  LTrue
     *  a = 1
     *  goto LEnd
     *  LFalse
     *  a = 0
     *  LEnd
     *  //End Assign
     *
     *  mais
     *
     *  a = 1 * 2 + 3
     *
     *  deviens
     *
     *  //expr
     *  t1 = 1 * 2
     *  t2 = t1 + 3
     *  //expr
     *  a = t2
     *
     *  et
     *
     *  if(b || c && !d)
     *
     *  deviens
     *
     *  //expr
     *  if b goto LTrue
     *  ifFalse c goto LFalse
     *  ifFalse d goto LTrue
     *  goto LFalse
     *  //expr
     *  //if
     *  LTrue
     *  codeS1
     *  goto lEnd
     *  LFalse
     *  codeS2
     *  LEnd
     *  //end if
     *
     *
     *  Il faut donc dès le départ vérifier dans la table de symbole le type de la variable à gauche, et généré du
     *  code différent selon ce type.
     *
     *  Pour avoir l'id de la variable de gauche de l'assignation, il peut être plus simple d'aller chercher la valeur
     *  du premier enfant sans l'accepter.
     *  De la sorte, on accept un noeud "Identifier" seulement lorsqu'on l'utilise comme référence (à droite d'une assignation)
     *  Cela simplifie le code de part et d'autre.
     *
     *  Aussi, il peut être pertinent d'extraire le code de l'assignation dans une fonction privée, parce que ce code
     *  sera utile pour les noeuds de comparaison (plus d'explication au commentaire du noeud en question.)
     *  la signature de la fonction que j'ai utilisé pour se faire est :
     *  private String generateAssignCode(Node node, String tId);
     *  ou "node" est le noeud de l'expression représentant la valeur, et tId est le nom de la variable ou assigné
     *  la valeur.
     *
     *  Il est normal (et probablement inévitable concidérant la structure de l'arbre)
     *  de généré inutilement des labels (ou des variables temporaire) qui ne sont pas utilisé ni imprimé dans le code résultant.
     */

    private String generateAssignCode(Node node, String tId) {
        //m_writer.println(node.lTrue);

        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data){
        String id = ((ASTIdentifier) node.jjtGetChild(0)).getValue(); // si on a a = 2, on a le id a
        BoolLabel B = new BoolLabel(id);

        if (data == null)
        {
            B.lTrue = genLabel();
            B.lFalse = genLabel();
        }

        if (SymbolTable.get(id).equals(VarType.Number)) // si a est numerique
        {
            m_writer.println(id + " = " + (String)node.jjtGetChild(1).jjtAccept(this, B)); // a = rhs
            if (data == null)
            {
                label -= 2; // pour les labels generated plus haut non utilises.
            }
        }
        else // si a est bool
        {
            String Snext = genLabel();
            node.jjtGetChild(1).jjtAccept(this, B);
            m_writer.println(B.lTrue);
            m_writer.println(B.id + " = " + "1"); // true
            m_writer.println("goto " + Snext);
            m_writer.println(B.lFalse); // false
            m_writer.println(B.id + " = " + "0"); // false code
            m_writer.print(Snext);
        }
        return null;
    }


    //Il n'y a probablement rien à faire ici
    @Override
    public Object visit(ASTExpr node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    //Expression arithmétique
    /*
    Les expressions arithmétique add et mult fonctionne exactement de la même manière. c'est pourquoi
    il est plus simple de remplir cette fonction une fois pour avoir le résultat pour les deux noeuds.

    On peut bouclé sur "ops" ou sur node.jjtGetNumChildren(),
    la taille de ops sera toujours 1 de moins que la taille de jjtGetNumChildren
     */
    public String exprCodeGen(SimpleNode node, Object data, Vector<String> ops) {
        String temp = null;
        String left = null;
        String right = null;
        if (ops.size() == 0)
        {
            return (String) node.jjtGetChild(0).jjtAccept(this, data);
        }

        for (int i = 0; i < ops.size() ; i++)
        {
            if ( i > 0 )
            {
                left = temp;
            }
            else
            {
                right = (String)node.jjtGetChild(0).jjtAccept(this, data);
                left = (String)node.jjtGetChild(i + 1).jjtAccept(this,data);
                temp = genId();
                m_writer.println(temp + " = " + left + " " + ops.get(i) + " " + right);
            }
        }

        return temp;
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        return exprCodeGen(node, data, node.getOps());
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        return exprCodeGen(node, data, node.getOps());
    }

    //UnaExpr est presque pareil au deux précédente. la plus grosse différence est qu'il ne va pas
    //chercher un deuxième noeud enfant pour avoir une valeur puisqu'il s'agit d'une opération unaire.
    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        if (data == null)
        {
            return node.jjtGetChild(0).jjtAccept(this, null);
        }
        String right = (String)node.jjtGetChild(0).jjtAccept(this, data);
        if (node.getOps().size() > 0)
        {
            String temp = genId();
            m_writer.println(temp + " = " + node.getOps().get(0) + " " + right);
            return temp;
        }
        return right;
    }

    //expression logique

    /*

    Rappel, dans le langague, le OU et le ET on la même priorité, et sont associatif à droite par défaut.
    ainsi :
    "a = a || || a2 || b && c || d" est interprété comme "a = a || a2 || (b && (c || d))"
     */
    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        if (node.jjtGetNumChildren() == 1 || data == null) return node.jjtGetChild(0).jjtAccept(this, data);
// fix this up
        BoolLabel B = (BoolLabel)data, B1 = null, B2 = null;
        if (node.getOps().get(0).equals("||"))
        {
            B1 = new BoolLabel(B.lTrue.equals("fall")? genLabel():B.lTrue, "fall", B.id);
            node.jjtGetChild(0).jjtAccept(this, B1);
            B2 = new BoolLabel(B.lTrue, B.lFalse, B.id);
            node.jjtGetChild(1).jjtAccept(this, B2);
            if(B.lTrue.equals("fall")) m_writer.println(B1.lTrue);
        } else if (node.getOps().get(0).equals("&&"))
        {
            B1 = new BoolLabel("fall", B.lFalse.equals("fall")? genLabel():B.lFalse, B.id);
            node.jjtGetChild(0).jjtAccept(this, B1);
            B2 = new BoolLabel(B.lTrue, B.lFalse, B.id);
            node.jjtGetChild(1).jjtAccept(this, B2);
            if(B.lFalse.equals("fall")) m_writer.println(B1.lFalse);
        }
        return null;
    }


    //cette fonction privé est utile parce que le code pour généré le goto pour les opérateurs de comparaison est le même
    //que celui pour le référencement de variable booléenne.
    //le code est très simple avant l'optimisation, mais deviens un peu plus long avec l'optimisation.
    private void genCodeRelTestJump(String labelTrue, String labelfalse, String strSegment) {
        if (labelTrue != null && labelfalse != null) {
            m_writer.println("if " + strSegment + " goto " + labelTrue);
            m_writer.println("goto " + labelfalse);
        } else if (labelTrue != null) {
            m_writer.println("if " + strSegment + " goto " + labelTrue);
        } else if (labelfalse != null) {
            m_writer.println("if " + strSegment + " goto " + labelfalse);
        }
    }


    //une partie de la fonction à été faite pour donner des pistes, mais comme tous le reste du fichier, tous est libre
    //à modification.
    /*
    À ajouté : la comparaison est plus complexe quand il s'agit d'une comparaison de booléen.
    Le truc est de :
    1. vérifier qu'il s'agit d'une comparaison de nombre ou de booléen.
        On peut Ce simplifier la vie et le déterminer simplement en regardant si les enfants retourne une valeur ou non, à condition
        de s'être assurer que les valeurs booléennes retourne toujours null.
     2. s'il s'agit d'une comparaison de nombre, on peut faire le code simple par "genCodeRelTestJump(B, test)"
     3. s'il s'agit d'une comparaison de booléen, il faut enregistrer la valeur gauche et droite de la comparaison dans une variable temporaire,
        en utilisant le même code que pour l'assignation, deux fois. (mettre ce code dans une fonction deviens alors pratique)
        avant de faire la comparaison "genCodeRelTestJump(B, test)" avec les deux variables temporaire.

        notez que cette méthodes peut sembler peu efficace pour certain cas, mais qu'avec des passes d'optimisations subséquente, (que l'on
        ne fera pas dans le cadre du TP), on pourrait s'assurer que le code produit est aussi efficace qu'il peut l'être.
     */
    @Override
    public Object visit(ASTCompExpr node, Object data) {
        if (node.jjtGetNumChildren() == 1)
        {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }
        BoolLabel B = (BoolLabel)data;
        String E1 = (String)node.jjtGetChild(0).jjtAccept(this, null);
        if (E1 != null) // numerique
        {
            E1 = (String)node.jjtGetChild(0).jjtAccept(this, data);
            String E2 = (String) node.jjtGetChild(1).jjtAccept(this, data);
            if (!B.lTrue.equals("fall") && !B.lFalse.equals("fall"))
            {
                String strSeg = E1 + " " + node.getValue() + " " + E2;
                genCodeRelTestJump(B.lTrue, B.lFalse, strSeg);
            }
            else {
                if (!B.lTrue.equals("fall")) {
                    String strSeg = E1 + " " + node.getValue() + " " + E2;
                    genCodeRelTestJump(B.lTrue, B.lFalse, strSeg);
                } else {
                    String strSeg = E1 + " " + node.getValue() + " " + E2;

                    if (!B.lFalse.equals("fall")) {
                        m_writer.println("ifFalse " + strSeg + " goto " + B.lFalse);
                    }
                }
            }
        } else { // Boolean
            String temp1 = genId();
            BoolLabel B1 = new BoolLabel(genLabel(), genLabel(), temp1);
            String next = genLabel();
            E1 = (String)node.jjtGetChild(0).jjtAccept(this, B1);
            m_writer.println(B1 + " = " + next);

            String temp2 = genId();
            BoolLabel B2 = new BoolLabel(genLabel(), genLabel(), temp2);
            next = genLabel();
            node.jjtGetChild(1).jjtAccept(this, B2);
            m_writer.println(B2 + " = " + next);
            String strSeg = temp1 + " " + node.getValue() + " " + temp2;
            genCodeRelTestJump(B.lTrue, B.lFalse, strSeg);
        }
        return null;

    }


    /*
    Même si on peut y avoir un grand nombre d'opération, celle-ci s'annullent entre elle.
    il est donc intéressant de vérifier si le nombre d'opération est pair ou impaire.
    Si le nombre d'opération est pair, on peut simplement ignorer ce noeud.
     */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        BoolLabel B = (BoolLabel) data;
        if (B != null && (node.getOps().size() % 2 != 0)) {
            return node.jjtGetChild(0).jjtAccept(this, new BoolLabel(B.lFalse, B.lTrue, B.id));
        }
        return node.jjtGetChild(0).jjtAccept(this, B);
    }

    @Override
    public Object visit(ASTGenValue node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    /*
    BoolValue ne peut pas simplement retourné sa valeur à son parent contrairement à GenValue et IntValue,
    Il doit plutôt généré des Goto direct, selon sa valeur.
     */
    @Override
    public Object visit(ASTBoolValue node, Object data) {
        if (data != null)
        {
            BoolLabel B = (BoolLabel)data;
            if (node.getValue().toString().equals("true") && !B.lTrue.equals("fall"))
            {
                m_writer.println("goto " + B.lTrue);
            }
            else if (node.getValue().toString().equals("false") && !B.lFalse.equals("fall"))
            {
                m_writer.println("goto " + B.lFalse);
            }
        }
        return null;
    }


    /*
    si le type de la variable est booléenne, il faudra généré des goto ici.
    le truc est de faire un "if value == 1 goto Label".
    en effet, la structure "if valeurBool goto Label" n'existe pas dans la syntaxe du code à trois adresse.
     */
    @Override
    public Object visit(ASTIdentifier node, Object data) {
        if(SymbolTable.get(node.getValue()) == VarType.Bool) {
            String test = node.getValue() + " == 1";
            if (data != null) {
                BoolLabel B = (BoolLabel)data;
                if (!B.lTrue.equals("fall") && !B.lFalse.equals("fall")) {
                    genCodeRelTestJump(B.lTrue, B.lFalse, test );
                } else {
                    if (!B.lTrue.equals("fall")) {
                        genCodeRelTestJump(B.lTrue, B.lFalse, test );
                    } else {
                        if (!B.lFalse.equals("fall")) {
                            m_writer.println("ifFalse " + test + " goto " + B.lFalse);
                        }
                    }
                }
            }
            return null;
        }
        return node.getValue();
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return Integer.toString(node.getValue());
    }


    @Override
    public Object visit(ASTSwitchStmt node, Object data) {
        String testLabel = genLabel(), next = genLabel();
        m_writer.println("goto " + testLabel);
        HashMap<String, String> cases = new HashMap<>();
        ArrayList<String> labels = new ArrayList<>();
        boolean hasDefault = false;
        for (int i=1; i<node.jjtGetNumChildren(); i++) {
            String caseLabel = genLabel();
            labels.add(caseLabel);
            m_writer.println(caseLabel);
            String target = (String) node.jjtGetChild(i).jjtAccept(this, null);
            if(target != null) cases.put(caseLabel, target); // case
            else hasDefault = true; // default
            m_writer.println("goto " + next);
        }
        m_writer.println(testLabel);
        for (int i=1; i<node.jjtGetNumChildren()-(hasDefault?1:0); i++){
            String caseLabel = labels.get(i-1), testValue = cases.get(caseLabel);
            m_writer.println("if " + node.jjtGetChild(0).jjtAccept(this, null) + " == " + testValue + " goto " + caseLabel);
        }
        if(hasDefault) m_writer.println("goto " + labels.get(labels.size()-1));
        m_writer.println(next);
        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        String lcase = (String)node.jjtGetChild(0).jjtAccept(this, data);
        node.childrenAccept(this, data);
        return lcase;
    }

    @Override
    public Object visit(ASTDefaultStmt node, Object data) {

        node.childrenAccept(this, null);

        return null;
    }

    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        Bool,
        Number
    }

    //utile surtout pour envoyé de l'informations au enfant des expressions logiques.
    private class BoolLabel {
        public String lTrue = null;
        public String lFalse = null;
        public String id = null;

        BoolLabel() {
            lTrue = genLabel();
            lFalse = genLabel();
        }

        BoolLabel(String id) {
            if (id != null) {
                this.id = id;
            }
        }

        BoolLabel(String lTrue, String lFalse, String id) {
            if (lTrue != null) {
                this.lTrue = lTrue;
            }
            if (lFalse != null) {
                this.lFalse = lFalse;
            }

            if (id != null) {
                this.id = id;
            }
        }

    }
}

