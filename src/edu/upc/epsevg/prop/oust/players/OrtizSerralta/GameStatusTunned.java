package edu.upc.epsevg.prop.oust.players.OrtizSerralta;

import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.PlayerType;
import java.awt.Point;

/**
 * Extensió de GameStatus amb una funció d'avaluació heurística personalitzada.
 * Aquesta classe s'utilitza principalment per a proves unitàries i validació
 * de l'heurística utilitzada pel jugador principal.
 */
public class GameStatusTunned extends GameStatus {

    public GameStatusTunned(GameStatus gs) {
        super(gs);
    }

    public GameStatusTunned(int n) {
        super(n);
    }

    /**
     * Calcula l'avaluació heurística de l'estat.
     * Utilitza la mateixa lògica de connectivitat i penalització que el jugador principal.
     * * @return Puntuació de l'estat (positiva si afavoreix al jugador actual).
     */
    public int getHeuristicEvaluation() {
        if (isGameOver()) {
            PlayerType winner = GetWinner();
            if (winner != null) {
                return (winner == getCurrentPlayer()) ? 10000000 : -10000000;
            }
            return 0; // Empat
        }

        int size = getSize();
        PlayerType me = getCurrentPlayer();
        
        boolean[][] visited = new boolean[size][size];
        
        double myScore = 0;
        double oppScore = 0;
        int myPieces = 0;
        int oppPieces = 0;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (!visited[i][j]) {
                    PlayerType p = getColor(i, j);
                    if (p != null) {
                        int groupSize = countGroupSize(i, j, p, visited, size);
                        
                        // Heurística exponencial per premiar grups grans
                        double val = Math.pow(groupSize, 2);
                        
                        if (p == me) {
                            myScore += val;
                            myPieces += groupSize;
                        } else {
                            // Penalització agressiva (x2) als grups del rival
                            oppScore += val * 2.0; 
                            oppPieces += groupSize;
                        }
                    }
                }
            }
        }
        
        // Puntuació base per material
        myScore += myPieces * 5;
        oppScore += oppPieces * 5;

        return (int) (myScore * 10 - oppScore * 10);
    }

    /**
     * Mètode recursiu per comptar la mida d'un grup de fitxes connectades.
     * * @param x Coordenada X.
     * @param y Coordenada Y.
     * @param p Tipus de jugador a comprovar.
     * @param visited Matriu de visitats per evitar cicles.
     * @param size Mida del tauler.
     * @return Nombre de fitxes connectades.
     */
    private int countGroupSize(int x, int y, PlayerType p, boolean[][] visited, int size) {
        if (x < 0 || y < 0 || x >= size || y >= size) return 0;
        if (visited[x][y]) return 0;
        if (getColor(x, y) != p) return 0;

        visited[x][y] = true;
        int count = 1;
        
        int[][] dirs = {{1,0}, {1,-1}, {0,-1}, {-1,0}, {-1,1}, {0,1}};

        for (int[] d : dirs) {
            count += countGroupSize(x + d[0], y + d[1], p, visited, size);
        }
        return count;
    }
}