/*******************************************************************************
 * Copyright (c) 2012 pf_miles.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     pf_miles - initial API and implementation
 ******************************************************************************/
package com.github.pfmiles.dropincc.impl.llstar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.pfmiles.dropincc.DropinccException;
import com.github.pfmiles.dropincc.impl.EleType;
import com.github.pfmiles.dropincc.impl.GruleType;
import com.github.pfmiles.dropincc.impl.TokenType;
import com.github.pfmiles.dropincc.impl.kleene.CKleeneNode;
import com.github.pfmiles.dropincc.impl.kleene.KleeneCrossType;
import com.github.pfmiles.dropincc.impl.kleene.KleeneStarType;
import com.github.pfmiles.dropincc.impl.kleene.KleeneType;
import com.github.pfmiles.dropincc.impl.kleene.OptionalType;
import com.github.pfmiles.dropincc.impl.util.SeqGen;

/**
 * The whole ATN network for the analyzing grammar
 * 
 * @author pf-miles
 * 
 */
public class Atn {
    // all states contained in this ATN network
    private Set<AtnState> states = new HashSet<AtnState>();
    // grule type to its corresponding start state mapping
    private Map<GruleType, AtnState> gruleTypeStartStateMapping = new HashMap<GruleType, AtnState>();
    // atn state to its belonging grule type mapping
    private Map<AtnState, GruleType> stateGruleTypeMapping = new HashMap<AtnState, GruleType>();
    // normal state naming sequence
    private SeqGen normalStateSeq = new SeqGen();
    // mapping from generated kleene grule type to ATN contact point
    private Map<GenedKleeneGruleType, AtnState> contactPointMapping;

    /**
     * Return all target AtnState of the specified transition edge.
     * 
     * @param edge
     * @return
     */
    public Set<AtnState> getAllDestinationsOf(Object edge) {
        if (edge instanceof GenedKleeneGruleType) {
            // if is a generated kleene grule type, return the corresponding
            // contact point
            if (!this.contactPointMapping.containsKey((GenedKleeneGruleType) edge))
                throw new DropinccException("Couldn't find ATN contact point for generated kleene grule, ERROR! KleenType: " + edge);
            Set<AtnState> ret = new HashSet<AtnState>();
            ret.add(this.contactPointMapping.get((GenedKleeneGruleType) edge));
            return ret;
        } else {
            Set<AtnState> ret = new HashSet<AtnState>();
            for (AtnState state : states) {
                if (state.getTransitions().containsKey(edge))
                    ret.addAll(state.getTransitions().get(edge));
            }
            return ret;
        }
    }

    /**
     * Return the start state of the specified grule type.
     * 
     * @param gruleType
     * @return
     */
    public AtnState getStartState(GruleType gruleType) {
        return this.gruleTypeStartStateMapping.get(gruleType);
    }

    /**
     * turn this ATN network to a dot file
     * 
     * @return
     */
    public String toDot() {
        // TODO to be implemented
        return toString();
    }

    public String toString() {
        return "Atn(" + this.states + ")";
    }

    /**
     * Return the gruleType the specified atnState belongs to
     * 
     * @param state
     * @return
     */
    public GruleType getGruleTypeByAtnState(AtnState state) {
        return this.stateGruleTypeMapping.get(state);
    }

    /**
     * create a start state for the specified grule
     * 
     * @param grule
     * @return
     */
    public AtnState newStartStateForGrule(GruleType grule) {
        AtnState ret = new AtnState("R" + grule.getDefIndex());
        this.states.add(ret);
        this.stateGruleTypeMapping.put(ret, grule);
        this.gruleTypeStartStateMapping.put(grule, ret);
        return ret;
    }

    /**
     * create a end state for the specified grule
     * 
     * @param grule
     * @return
     */
    public AtnState newEndStateForGrule(GruleType grule) {
        AtnState ret = new AtnState("R" + grule.getDefIndex() + "_end", true);
        this.states.add(ret);
        this.stateGruleTypeMapping.put(ret, grule);
        return ret;
    }

    /**
     * create 'alt state' for the specified grule
     * 
     * @param grule
     * @param i
     * @return
     */
    public AtnState newAltStateForGrule(GruleType grule, int i) {
        AtnState ret = new AtnState("R" + grule.getDefIndex() + "_" + i);
        this.states.add(ret);
        this.stateGruleTypeMapping.put(ret, grule);
        return ret;
    }

    /**
     * Create a 'normal'(not start or end state or alt state of a grule) state
     * in the ATN, the names of these normal states are generated by a number
     * sequence global to the whole ATN network.
     * 
     * @param grule
     *            the grule the state belongs to
     * @return
     */
    public AtnState newAtnState(GruleType grule) {
        AtnState ret = new AtnState(String.valueOf(this.normalStateSeq.next()));
        this.states.add(ret);
        this.stateGruleTypeMapping.put(ret, grule);
        return ret;
    }

    /**
     * Generate all transitions from start to end with the specified edges
     * 
     * @param start
     * @param edges
     * @param end
     * @param grule
     * @param kleeneTypeToNode
     * @param contactPoints
     */
    public void genTransitions(AtnState start, List<EleType> edges, AtnState end, GruleType grule, Map<KleeneType, CKleeneNode> kleeneTypeToNode,
            Map<KleeneType, AtnState> contactPoints) {
        AtnState curState = start;
        EleType lastEdge = edges.get(edges.size() - 1);
        // iterate from 0 to size-1(except the last one)
        for (int i = 0; i < edges.size() - 1; i++) {
            EleType edge = edges.get(i);
            if (edge instanceof TokenType || edge instanceof GruleType) {
                AtnState nextState = this.newAtnState(grule);
                curState.addTransition(edge, nextState);
                curState = nextState;
            } else if (edge instanceof KleeneStarType) {
                this.genTransitions(curState, kleeneTypeToNode.get((KleeneStarType) edge).getContents(), curState, grule, kleeneTypeToNode, contactPoints);
                AtnState nextState = this.newAtnState(grule);
                curState.addTransition(Constants.epsilon, nextState);
                curState = nextState;
                contactPoints.put((KleeneStarType) edge, curState);
            } else if (edge instanceof KleeneCrossType) {
                List<EleType> content = kleeneTypeToNode.get((KleeneCrossType) edge).getContents();
                AtnState nextState = this.newAtnState(grule);
                this.genTransitions(curState, content, nextState, grule, kleeneTypeToNode, contactPoints);
                curState = nextState;
                this.genTransitions(curState, content, curState, grule, kleeneTypeToNode, contactPoints);
                nextState = this.newAtnState(grule);
                curState.addTransition(Constants.epsilon, nextState);
                curState = nextState;
                contactPoints.put((KleeneCrossType) edge, curState);
            } else if (edge instanceof OptionalType) {
                List<EleType> contents = kleeneTypeToNode.get((OptionalType) edge).getContents();
                AtnState nextState = this.newAtnState(grule);
                this.genTransitions(curState, contents, nextState, grule, kleeneTypeToNode, contactPoints);
                curState.addTransition(Constants.epsilon, nextState);
                curState = nextState;
                contactPoints.put((OptionalType) edge, curState);
            } else {
                throw new DropinccException("Illegal transition edge of ATN: " + edge);
            }
        }
        // build the last transition
        if (lastEdge instanceof TokenType || lastEdge instanceof GruleType) {
            curState.addTransition(lastEdge, end);
        } else if (lastEdge instanceof KleeneStarType) {
            this.genTransitions(curState, kleeneTypeToNode.get((KleeneStarType) lastEdge).getContents(), curState, grule, kleeneTypeToNode, contactPoints);
            curState.addTransition(Constants.epsilon, end);
            contactPoints.put((KleeneStarType) lastEdge, end);
        } else if (lastEdge instanceof KleeneCrossType) {
            List<EleType> content = kleeneTypeToNode.get((KleeneCrossType) lastEdge).getContents();
            AtnState nextState = this.newAtnState(grule);
            this.genTransitions(curState, content, nextState, grule, kleeneTypeToNode, contactPoints);
            curState = nextState;
            this.genTransitions(curState, content, curState, grule, kleeneTypeToNode, contactPoints);
            curState.addTransition(Constants.epsilon, end);
            contactPoints.put((KleeneCrossType) lastEdge, end);
        } else if (lastEdge instanceof OptionalType) {
            List<EleType> contents = kleeneTypeToNode.get((OptionalType) lastEdge).getContents();
            this.genTransitions(curState, contents, end, grule, kleeneTypeToNode, contactPoints);
            curState.addTransition(Constants.epsilon, end);
            contactPoints.put((OptionalType) lastEdge, end);
        } else {
            throw new DropinccException("Illegal transition edge of ATN: " + lastEdge);
        }
    }

    public Set<AtnState> getStates() {
        return states;
    }

    public void setContactPointMapping(Map<GenedKleeneGruleType, AtnState> contactPointMapping) {
        this.contactPointMapping = contactPointMapping;
    }
}
