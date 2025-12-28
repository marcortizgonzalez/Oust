package edu.upc.epsevg.prop.oust.players.OrtizSerralta;

import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.PlayerMove;
import edu.upc.epsevg.prop.oust.SearchType;
import java.awt.Point;
import java.util.List;

/**
 * Jugador que implementa Iterative Deepening Search (IDS).
 * <p>
 * Aquesta classe estén {@link PlayerMiniMax} per afegir la gestió del temps.
 * Realitza cerques incrementals en profunditat fins que s'esgota el temps
 * disponible (5 segons).
 * </p>
 * @author OrtizSerralta
 */
public class PlayerMiniMaxIDS extends PlayerMiniMax {

    /**
     * Constructor buit (Requerit per l'especificació).
     * Inicialitza el jugador amb nom "Terminator" i configura
     * la profunditat fixa a -1 per habilitar el mode IDS.
     */
    public PlayerMiniMaxIDS() {
        super(); // Crida al constructor protegit de la classe pare
        this.name = "OrtizSerralta (Terminator)";
    }

    /**
     * Executa l'algorisme IDS per trobar el millor moviment dins del temps límit.
     * Utilitza finestres d'aspiració (Aspiration Windows) basades en la puntuació
     * de la iteració anterior per accelerar la cerca.
     * * @param s Estat actual del joc.
     * @return El millor moviment trobat.
     */
    @Override
    public PlayerMove move(GameStatus s) {
        this.timedOut = false;
        this.nodesExplored = 0;
        
        super.initStructures(s);

        List<Point> bestMoveSequence = null;
        int currentMaxDepth = 1;
        int previousScore = 0;

        // Optimització: Si només hi ha un moviment possible, el retornem immediatament
        if (s.getMoves().size() == 1) {
            return new PlayerMove(getSafeSequence(s), 0, 0, SearchType.MINIMAX_IDS);
        }

        // Bucle d'aprofundiment iteratiu
        while (!timedOut) {
            int alpha = Integer.MIN_VALUE;
            int beta = Integer.MAX_VALUE;

            // Finestres d'aspiració: estretim la finestra al voltant de la puntuació prèvia
            if (currentMaxDepth > 2 && bestMoveSequence != null) {
                int window = 50; 
                alpha = previousScore - window;
                beta = previousScore + window;
            }

            // Crida al motor Minimax de la classe pare
            Result res = super.minimax(s, currentMaxDepth, alpha, beta, 0, true);

            // Si el resultat cau fora de la finestra, repetim la cerca amb finestra completa
            if (!timedOut && currentMaxDepth > 2 && bestMoveSequence != null) {
                if (res.score <= alpha || res.score >= beta) {
                    res = super.minimax(s, currentMaxDepth, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, true);
                }
            }

            // Si la iteració s'ha completat sense timeout, guardem el resultat
            if (!timedOut) {
                if (res.sequence != null && !res.sequence.isEmpty()) {
                    bestMoveSequence = res.sequence;
                    previousScore = res.score;
                }
                
                // Si trobem una victòria assegurada, tallem per estalviar temps
                if (res.score > 900000) break;
                
                currentMaxDepth++;
                // Límit de seguretat de profunditat
                if (currentMaxDepth > 60) break;
            }
        }

        // Sistema de recuperació: Si no tenim cap moviment vàlid, en generem un de segur
        if (bestMoveSequence == null || bestMoveSequence.isEmpty()) {
             bestMoveSequence = getSafeSequence(s);
        }

        return new PlayerMove(bestMoveSequence, nodesExplored, currentMaxDepth - 1, SearchType.MINIMAX_IDS);
    }
}