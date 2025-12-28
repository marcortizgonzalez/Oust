package edu.upc.epsevg.prop.oust.players.OrtizSerralta;

import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.IAuto;
import edu.upc.epsevg.prop.oust.IPlayer;
import edu.upc.epsevg.prop.oust.PlayerMove;
import edu.upc.epsevg.prop.oust.PlayerType;
import edu.upc.epsevg.prop.oust.SearchType;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Motor d'Intel·ligència Artificial per al joc Oust.
 * <p>
 * Aquesta classe implementa l'algorisme Minimax optimitzat amb 
 * <b>Principal Variation Search (PVS)</b>, també conegut com a NegaScout.
 * Utilitza diverses tècniques de millora de rendiment com Taules de Transposició (TT),
 * Heurístiques d'Història i Killer Moves per a l'ordenació de moviments.
 * </p>
 * <p>
 * L'avaluació de l'estat es basa en la formació de grups de fitxes (connectivitat)
 * i la mobilitat disponible.
 * </p>
 * * @author OrtizSerralta
 */
public class PlayerMiniMax implements IPlayer, IAuto {

    protected String name = "OrtizSerralta Fixed";
    protected boolean timedOut;
    protected long nodesExplored;
    protected PlayerType rootPlayer;
    protected int maxBoardDim;
    
    /**
     * Profunditat fixa de cerca. Si és -1, indica que s'està utilitzant
     * en mode IDS (Iterative Deepening Search) gestionat per la subclasse.
     */
    protected int fixedDepth = -1;

    // --- Taula de Transposició (TT) ---
    private static final int TT_SIZE = 1 << 20; // Aproximadament 1 milió d'entrades
    
    /**
     * Taula de Transposició estàtica per compartir coneixement entre torns.
     */
    protected static final TTEntry[] tt = new TTEntry[TT_SIZE];

    protected static final int FLAG_EXACT = 0;
    protected static final int FLAG_LOWERBOUND = 1;
    protected static final int FLAG_UPPERBOUND = 2;

    /**
     * Estructura per emmagatzemar informació d'un estat a la Taula de Transposició.
     */
    protected static class TTEntry {
        long key;                 // Clau Zobrist per identificar l'estat únic
        int score;                // Puntuació emmagatzemada
        int depth;                // Profunditat a la qual es va trobar aquesta puntuació
        int flag;                 // Tipus de cota (Exacta, Alpha o Beta)
        List<Point> bestSequence; // Millor seqüència de moviments des d'aquest estat
    }

    // --- Estructures Auxiliars ---
    protected Point[][] killerMoves;       // Moviments que han provocat podes (Killer Heuristic)
    protected int[][] historyHeuristic;    // Taula d'història per ordenar moviments segons èxit previ
    protected boolean[][] visitedBuffer;   // Buffer per evitar re-assignació de memòria en l'heurística

    /**
     * Constructor que estableix una profunditat màxima fixa.
     * * @param profunditatMaxima La profunditat límit per a la cerca Minimax.
     */
    public PlayerMiniMax(int profunditatMaxima) {
        this.fixedDepth = profunditatMaxima;
    }
    
    /**
     * Constructor protegit per a ús de la subclasse IDS.
     * Inicialitza la profunditat fixa a -1 per indicar mode de temps.
     */
    protected PlayerMiniMax() {
        this.fixedDepth = -1; 
    }

    /**
     * Decideix el millor moviment per a l'estat actual del joc.
     * Si la profunditat està fixada, executa una cerca directa.
     * També inclou mecanismes de seguretat contra errors d'execució.
     * * @param s L'estat actual del joc (GameStatus).
     * @return L'objecte PlayerMove amb la seqüència de moviments escollida.
     */
    @Override
    public PlayerMove move(GameStatus s) {
        initStructures(s);
        nodesExplored = 0;
        timedOut = false;

        // Optimització: si només hi ha un moviment possible, no cal cercar
        if (s.getMoves().size() == 1) {
            return new PlayerMove(getSafeSequence(s), 0, 0, SearchType.MINIMAX);
        }

        // Execució de l'algorisme
        Result res = minimax(s, fixedDepth, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, true);
        
        // Verificació de seguretat: si la seqüència és buida o nul·la, generem una vàlida
        List<Point> sequence = res.sequence;
        if (sequence == null || sequence.isEmpty()) {
            sequence = getSafeSequence(s);
        }

        return new PlayerMove(sequence, nodesExplored, fixedDepth, SearchType.MINIMAX);
    }

    /**
     * Notifica al jugador que s'ha esgotat el temps.
     */
    @Override
    public void timeout() { this.timedOut = true; }

    /**
     * Retorna el nom del jugador.
     * @return Nom identificatiu.
     */
    @Override
    public String getName() { return name; }

    /**
     * Inicialitza o neteja les estructures de dades necessàries per al torn.
     * Gestiona la memòria dels buffers i taules heurístiques.
     * * @param s Estat actual per determinar mides del tauler.
     */
    protected void initStructures(GameStatus s) {
        this.rootPlayer = s.getCurrentPlayer();
        int size = s.getSize();
        this.maxBoardDim = size * 2 + 2;
        
        if (visitedBuffer == null || visitedBuffer.length != size) {
            visitedBuffer = new boolean[size][size];
        }
        
        // Neteja parcial dels Killer Moves
        if (killerMoves == null || killerMoves.length < 100) {
            killerMoves = new Point[100][2];
        } else {
            for(int i=0; i<killerMoves.length; i++) {
                killerMoves[i][0] = null;
                killerMoves[i][1] = null;
            }
        }
        
        // Decaïment de la taula d'història per adaptar-se a la nova fase del joc
        if (historyHeuristic == null || historyHeuristic.length != maxBoardDim) {
            historyHeuristic = new int[maxBoardDim][maxBoardDim];
        } else {
             for(int i=0; i<maxBoardDim; i++) 
                for(int j=0; j<maxBoardDim; j++) 
                    historyHeuristic[i][j] /= 8;
        }
    }

    /**
     * Implementació de l'algorisme Principal Variation Search (PVS).
     * Realitza una cerca recursiva intentant provar el primer moviment amb finestra completa
     * i la resta amb finestra nul·la (Null Window) per accelerar les podes.
     * * @param s Estat actual del joc.
     * @param depth Profunditat restant per explorar.
     * @param alpha Valor Alpha (cota inferior).
     * @param beta Valor Beta (cota superior).
     * @param ply Profunditat actual des de l'arrel (per a distància de victòria).
     * @param allowNull Indica si es permet fer Null Window Search (no utilitzat en aquesta versió base).
     * @return Objecte Result amb la millor puntuació i la seqüència de moviments.
     */
    protected Result minimax(GameStatus s, int depth, int alpha, int beta, int ply, boolean allowNull) {
        // En mode profunditat fixa, ignorem el flag de temps si no s'indica el contrari
        if (fixedDepth == -1 && timedOut) return new Result(0, null);
        
        nodesExplored++;

        // --- 1. Consulta a la Taula de Transposició (TT) ---
        long zobristKey = s.hashCode(); 
        int ttIndex = (int) ((zobristKey & 0x7FFFFFFFFFFFFFFFL) % TT_SIZE);
        TTEntry entry = tt[ttIndex];
        List<Point> ttMove = null;

        if (entry != null && entry.key == zobristKey && entry.depth >= depth) {
            if (entry.flag == FLAG_EXACT) return new Result(entry.score, entry.bestSequence);
            if (entry.flag == FLAG_LOWERBOUND) alpha = Math.max(alpha, entry.score);
            if (entry.flag == FLAG_UPPERBOUND) beta = Math.min(beta, entry.score);
            if (alpha >= beta) return new Result(entry.score, entry.bestSequence);
            ttMove = entry.bestSequence; 
        }

        // --- 2. Casos Base ---
        if (s.isGameOver()) {
            // Puntuació molt alta/baixa ajustada per ply per preferir victòries ràpides
            return new Result(s.GetWinner() == rootPlayer ? 1000000 - ply : -1000000 + ply, null);
        }

        if (depth <= 0) {
             return new Result(heuristic(s), null);
        }

        List<Point> moves = s.getMoves();
        if (moves.isEmpty()) return new Result(heuristic(s), null); 

        // --- 3. Ordenació de Moviments ---
        Point hashPoint = (ttMove != null && !ttMove.isEmpty()) ? ttMove.get(0) : null;
        final int currentPly = ply; 
        
        moves.sort((p1, p2) -> {
            if (p1.equals(hashPoint)) return -1; // Moviment de la TT primer
            if (p2.equals(hashPoint)) return 1;
            
            boolean k1 = isKiller(currentPly, p1);
            boolean k2 = isKiller(currentPly, p2);
            if (k1 && !k2) return -1; // Killer moves segon
            if (!k1 && k2) return 1;
            
            // Heurística d'història tercer
            int h1 = (p1.x < maxBoardDim && p1.y < maxBoardDim) ? historyHeuristic[p1.x][p1.y] : 0;
            int h2 = (p2.x < maxBoardDim && p2.y < maxBoardDim) ? historyHeuristic[p2.x][p2.y] : 0;
            return h2 - h1; 
        });

        // --- 4. Cerca Recursiva PVS ---
        List<Point> bestSeq = null;
        int bestVal = (s.getCurrentPlayer() == rootPlayer) ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int originalAlpha = alpha;
        boolean firstMove = true;
        boolean isMax = (s.getCurrentPlayer() == rootPlayer);

        for (Point p : moves) {
            if (fixedDepth == -1 && timedOut) break;

            GameStatus next = new GameStatus(s);
            
            // Bloc try-catch per protegir contra errors interns de la llibreria GameStatus
            try {
                next.placeStone(p);
            } catch (Exception e) {
                continue; // Si el moviment provoca error, el saltem
            }

            boolean sameTurn = (next.getCurrentPlayer() == s.getCurrentPlayer());

            Result childRes;
            if (firstMove || sameTurn) {
                // Finestra completa per al primer node o si repetim torn
                int nextDepth = sameTurn ? depth : depth - 1;
                childRes = minimax(next, nextDepth, alpha, beta, ply + 1, true);
            } else {
                // Finestra Nul·la (Null Window Search) per a la resta
                childRes = minimax(next, depth - 1, alpha, alpha + 1, ply + 1, false);
                if (isMax) {
                    // Si falla la hipòtesi (trobem millor), re-cerca amb finestra completa
                    if (childRes.score > alpha && childRes.score < beta) {
                        childRes = minimax(next, depth - 1, alpha, beta, ply + 1, true);
                    }
                } else {
                     if (childRes.score < beta && childRes.score > alpha) {
                        childRes = minimax(next, depth - 1, alpha, beta, ply + 1, true);
                     }
                }
            }
            firstMove = false;

            // Actualització de valors Alpha-Beta
            if (isMax) {
                if (childRes.score > bestVal) {
                    bestVal = childRes.score;
                    bestSeq = new ArrayList<>();
                    bestSeq.add(p);
                    if (sameTurn && childRes.sequence != null) bestSeq.addAll(childRes.sequence);
                }
                alpha = Math.max(alpha, bestVal);
            } else {
                if (childRes.score < bestVal) {
                    bestVal = childRes.score;
                    bestSeq = new ArrayList<>();
                    bestSeq.add(p);
                    if (sameTurn && childRes.sequence != null) bestSeq.addAll(childRes.sequence);
                }
                beta = Math.min(beta, bestVal);
            }

            // Poda Beta
            if (beta <= alpha) {
                if (!sameTurn) { 
                    storeKiller(ply, p);
                    updateHistory(p, depth);
                }
                break; 
            }
        }

        // --- 5. Emmagatzematge a la TT ---
        if (fixedDepth != -1 || !timedOut) {
            TTEntry newEntry = new TTEntry();
            newEntry.key = zobristKey;
            newEntry.score = bestVal;
            newEntry.depth = depth;
            newEntry.bestSequence = bestSeq;
            if (bestVal <= originalAlpha) newEntry.flag = FLAG_UPPERBOUND;
            else if (bestVal >= beta) newEntry.flag = FLAG_LOWERBOUND;
            else newEntry.flag = FLAG_EXACT;
            tt[ttIndex] = newEntry;
        }

        return new Result(bestVal, bestSeq);
    }

    /**
     * Funció d'Avaluació Heurística.
     * <p>Calcula una puntuació per a l'estat actual basada en:</p>
     * <ul>
     * <li><b>Connectivitat:</b> Grandària dels grups de fitxes (elevat al quadrat).</li>
     * <li><b>Agressivitat:</b> Penalització doble als grups del rival.</li>
     * <li><b>Mobilitat:</b> Nombre de moviments disponibles.</li>
     * </ul>
     * * @param s Estat del joc a avaluar.
     * @return Puntuació entera (positiva favorable al jugador arrel).
     */
    protected int heuristic(GameStatus s) {
        int size = s.getSize();
        // Neteja ràpida del buffer de visitats
        for(int i=0; i<size; i++) 
            for(int j=0; j<size; j++) visitedBuffer[i][j] = false;
        
        double myScore = 0;
        double oppScore = 0;
        int myPieces = 0;
        int oppPieces = 0;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (!visitedBuffer[i][j]) {
                    PlayerType p = s.getColor(i, j);
                    if (p != null) {
                        int groupSize = countGroupSize(s, i, j, p);
                        // Fórmula quadràtica per potenciar grups grans i sòlids
                        double val = Math.pow(groupSize, 2);
                        if (p == rootPlayer) {
                            myScore += val;
                            myPieces += groupSize;
                        } else {
                            oppScore += val * 2.0; // Penalització agressiva
                            oppPieces += groupSize;
                        }
                    }
                }
            }
        }
        
        // Bonus per material total
        myScore += myPieces * 5;
        oppScore += oppPieces * 5;
        
        // Bonus per mobilitat (diferència d'opcions de moviment)
        int mobilityBonus = s.getMoves().size() * 10;
        
        return (int) (myScore * 10 - oppScore * 10) + mobilityBonus;
    }

    /**
     * Calcula recursivament la mida d'un grup de fitxes connectades.
     * * @param s Estat del joc.
     * @param x Coordenada X.
     * @param y Coordenada Y.
     * @param p Jugador propietari de la fitxa.
     * @return Nombre de fitxes en el grup.
     */
    protected int countGroupSize(GameStatus s, int x, int y, PlayerType p) {
        if (x < 0 || y < 0 || x >= s.getSize() || y >= s.getSize()) return 0;
        if (visitedBuffer[x][y]) return 0;
        if (s.getColor(x, y) != p) return 0;
        visitedBuffer[x][y] = true;
        int count = 1;
        // Veïns hexagonals
        int[][] dirs = {{1,0}, {1,-1}, {0,-1}, {-1,0}, {-1,1}, {0,1}};
        for (int[] d : dirs) count += countGroupSize(s, x + d[0], y + d[1], p);
        return count;
    }

    // --- Mètodes Auxiliars per Heurístiques d'Ordenació ---
    
    protected boolean isKiller(int ply, Point p) {
        if (ply >= killerMoves.length) return false;
        if (killerMoves[ply][0] != null && killerMoves[ply][0].equals(p)) return true;
        return killerMoves[ply][1] != null && killerMoves[ply][1].equals(p);
    }

    protected void storeKiller(int ply, Point p) {
        if (ply >= killerMoves.length) return;
        if (killerMoves[ply][0] != null && killerMoves[ply][0].equals(p)) return;
        killerMoves[ply][1] = killerMoves[ply][0];
        killerMoves[ply][0] = p;
    }

    protected void updateHistory(Point p, int depth) {
        if (p.x < maxBoardDim && p.y < maxBoardDim) {
            historyHeuristic[p.x][p.y] += depth * depth;
            if (historyHeuristic[p.x][p.y] > 10000000) { 
                 for(int i=0; i<maxBoardDim; i++) 
                     for(int j=0; j<maxBoardDim; j++) historyHeuristic[i][j] /= 2;
            }
        }
    }
    
    /**
     * Genera una seqüència de moviments vàlida de manera segura.
     * S'utilitza com a sistema de recuperació (fallback) quan el temps s'esgota
     * o es produeix una excepció durant el càlcul principal.
     * * @param s Estat actual del joc.
     * @return Llista de punts que formen una seqüència legal completa.
     */
    protected List<Point> getSafeSequence(GameStatus s) {
        List<Point> sequence = new ArrayList<>();
        GameStatus aux = new GameStatus(s);
        
        boolean turnFinished = false;
        while (!turnFinished) {
            List<Point> moves = aux.getMoves();
            if (moves.isEmpty()) break;
            
            // Selecció voraç (el primer disponible)
            Point p = moves.get(0);
            
            try {
                aux.placeStone(p);
                sequence.add(p);
            } catch (Exception e) {
                break;
            }
            
            if (aux.isGameOver() || aux.getCurrentPlayer() != s.getCurrentPlayer()) {
                turnFinished = true;
            }
        }
        return sequence;
    }

    /**
     * Classe interna per emmagatzemar el resultat de la cerca.
     */
    protected static class Result {
        int score;
        List<Point> sequence;
        public Result(int s, List<Point> seq) { score = s; sequence = seq; }
    }
}