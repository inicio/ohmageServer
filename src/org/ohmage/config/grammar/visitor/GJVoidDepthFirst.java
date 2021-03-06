//
// Generated by JTB 1.3.2
//

package org.ohmage.config.grammar.visitor;
import java.util.Enumeration;

import org.ohmage.config.grammar.syntaxtree.Condition;
import org.ohmage.config.grammar.syntaxtree.Conjunction;
import org.ohmage.config.grammar.syntaxtree.Expression;
import org.ohmage.config.grammar.syntaxtree.Id;
import org.ohmage.config.grammar.syntaxtree.Node;
import org.ohmage.config.grammar.syntaxtree.NodeList;
import org.ohmage.config.grammar.syntaxtree.NodeListOptional;
import org.ohmage.config.grammar.syntaxtree.NodeOptional;
import org.ohmage.config.grammar.syntaxtree.NodeSequence;
import org.ohmage.config.grammar.syntaxtree.NodeToken;
import org.ohmage.config.grammar.syntaxtree.Sentence;
import org.ohmage.config.grammar.syntaxtree.SentencePrime;
import org.ohmage.config.grammar.syntaxtree.Start;
import org.ohmage.config.grammar.syntaxtree.Value;

/**
 * Provides default methods which visit each node in the tree in depth-first
 * order.  Your visitors may extend this class.
 */
public class GJVoidDepthFirst<A> implements GJVoidVisitor<A> {
	//
	// Auto class visitors--probably don't need to be overridden.
	//
	public void visit(NodeList n, A argu) {
		for ( Enumeration<Node> e = n.elements(); e.hasMoreElements(); ) {
			e.nextElement().accept(this,argu);
		}
	}

	public void visit(NodeListOptional n, A argu) {
		if ( n.present() ) {
			for ( Enumeration<Node> e = n.elements(); e.hasMoreElements(); ) {
				e.nextElement().accept(this,argu);
			}
		}
	}

	public void visit(NodeOptional n, A argu) {
		if ( n.present() )
			n.node.accept(this,argu);
	}

	public void visit(NodeSequence n, A argu) {
		for ( Enumeration<Node> e = n.elements(); e.hasMoreElements(); ) {
			e.nextElement().accept(this,argu);
		}
	}

	public void visit(NodeToken n, A argu) {}

	//
	// User-generated visitor methods below
	//

	/**
	 * f0 -> Sentence()
	 * f1 -> <EOF>
	 */
	public void visit(Start n, A argu) {
		n.f0.accept(this, argu);
		n.f1.accept(this, argu);
	}

	/**
	 * f0 -> Expression() SentencePrime()
	 *       | "(" Sentence() ")" SentencePrime()
	 */
	public void visit(Sentence n, A argu) {
		n.f0.accept(this, argu);
	}

	/**
	 * f0 -> ( Conjunction() Sentence() SentencePrime() )?
	 */
	public void visit(SentencePrime n, A argu) {
		n.f0.accept(this, argu);
	}

	/**
	 * f0 -> Id()
	 * f1 -> Condition()
	 * f2 -> Value()
	 */
	public void visit(Expression n, A argu) {
		n.f0.accept(this, argu);
		n.f1.accept(this, argu);
		n.f2.accept(this, argu);
	}

	/**
	 * f0 -> <TEXT>
	 */
	public void visit(Id n, A argu) {
		n.f0.accept(this, argu);
	}

	/**
	 * f0 -> "=="
	 *       | "!="
	 *       | "<"
	 *       | ">"
	 *       | "<="
	 *       | ">="
	 */
	public void visit(Condition n, A argu) {
		n.f0.accept(this, argu);
	}

	/**
	 * f0 -> <TEXT>
	 */
	public void visit(Value n, A argu) {
		n.f0.accept(this, argu);
	}

	/**
	 * f0 -> "and"
	 *       | "or"
	 */
	public void visit(Conjunction n, A argu) {
		n.f0.accept(this, argu);
	}

}
