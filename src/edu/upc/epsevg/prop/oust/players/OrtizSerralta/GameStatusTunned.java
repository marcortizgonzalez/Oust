package edu.upc.epsevg.prop.oust.players.OrtizSerralta;

import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.PlayerType;
import java.awt.Point;

public class GameStatusTunned extends GameStatus {

    public GameStatusTunned(GameStatus gs) {
        super(gs);
    }

    public GameStatusTunned(int n) {
        super(n);
    }

    /**
     * Heurística: Cuenta manual de piedras y arreglos de sintaxis.
     */
    public int getHeuristicValue() {
        // 1. Detección de final de partida
        if (isGameOver()) {
            PlayerType winner = GetWinner();
            if (winner == getCurrentPlayer()) return 100000;
            // Usar .opposite() sobre la instancia, no estático
            if (winner == getCurrentPlayer().opposite()) return -100000;
            return 0;
        }

        PlayerType me = getCurrentPlayer();
        // Usar .opposite() sobre la instancia
        PlayerType opp = me.opposite();

        // 2. Material (Contamos las piedras manualmente porque getScore falla)
        int myStones = 0;
        int oppStones = 0;
        int size = getSize(); // Obtenemos el tamaño del tablero

        // Recorremos el tablero hexagonal (Lógica extraída de Board.java)
        for (int i = 0; i < 2 * size - 1; i++) {
            int j = Math.max((i - size) + 1, 0);
            int j_end = Math.min(size + i, 2 * size - 1);
            
            for (; j < j_end; j++) {
                PlayerType color = getColor(i, j); // Obtenemos quién ocupa la casilla
                if (color == me) {
                    myStones++;
                } else if (color == opp) {
                    oppStones++;
                }
            }
        }

        // Diferencia de piezas * 100
        int materialScore = (myStones - oppStones) * 100;

        // 3. Movilidad * 10
        int mobilityScore = getMoves().size() * 10;

        return materialScore + mobilityScore;
    }
}