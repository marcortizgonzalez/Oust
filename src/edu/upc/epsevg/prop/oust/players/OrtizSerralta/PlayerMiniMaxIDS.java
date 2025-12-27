package edu.upc.epsevg.prop.oust.players.OrtizSerralta;

import edu.upc.epsevg.prop.oust.GameStatus;
import edu.upc.epsevg.prop.oust.PlayerMove;
import edu.upc.epsevg.prop.oust.SearchType;
import java.awt.Point;
import java.util.List;

/**
 * Jugador que usa Iterative Deepening Search (IDS).
 * Reutiliza la lógica Minimax pero incrementa la profundidad progresivamente
 * hasta que se agota el tiempo.
 */
public class PlayerMiniMaxIDS extends PlayerMiniMax {

    // Constructor vacío obligatorio
    public PlayerMiniMaxIDS() {
        // Llamamos al padre con una profundidad inicial irrelevante (se sobreescribirá)
        super(1); 
        this.name = "OrtizSerralta_IDS";
    }

    @Override
    public PlayerMove move(GameStatus s) {
        // Reiniciamos flags
        this.timedOut = false;
        this.nodesExplored = 0;
        
        // Empezamos con profundidad 1
        int currentDepth = 1;
        PlayerMove bestMoveSoFar = null;
        
        // Mientras no nos corten el tiempo...
        while (!this.timedOut) {
            
            // Actualizamos la profundidad máxima que usará el algoritmo minimax heredado
            this.maxDepth = currentDepth;
            
            // Ejecutamos el Minimax de la clase padre
            PlayerMove currentResult = super.move(s);
            
            // Verificación post-búsqueda:
            // Si minimax terminó PORQUE se acabó el tiempo (timedOut es true),
            // el resultado 'currentResult' puede ser incompleto o malo. Lo descartamos.
            if (!this.timedOut) {
                // Si terminó bien, guardamos este movimiento como el mejor hasta ahora
                bestMoveSoFar = currentResult;
                
                // Actualizamos estadísticas globales para que se vean en la UI
                // (Sumamos los nodos de esta iteración al total, aunque es aprox)
                // Nota: super.move ya resetea nodesExplored, así que aquí acumulamos visualmente si quisiéramos,
                // pero por simplicidad devolvemos los del último nivel completo.
                
                // Preparamos siguiente nivel
                currentDepth++;
                
                // OPTIMIZACIÓN: Si encontramos una victoria segura, ¿para qué seguir buscando?
                // En Oust es difícil saberlo seguro sin score perfecto, pero si limpiamos el tablero...
                // (Opcional: Si el score es +/- 100000, hacemos break)
                // IPlayer no expone el score fácilmente en el PlayerMove sin castear, 
                // pero si quisiéramos hilar fino, podríamos mirar currentResult.getScore() (si lo hubiéramos guardado).
            } 
            
            // Límite de seguridad por si el juego es muy simple o la máquina muy rápida
            if (currentDepth > 100) break;
        }
        
        // Si por alguna razón IDS falló en la profundidad 1 (ej. timeout inmediato de 0ms),
        // llamamos a un movimiento de emergencia (Random) para no crashear.
        if (bestMoveSoFar == null) {
            // Fallback de emergencia
            return new edu.upc.epsevg.prop.oust.players.RandomPlayer("Emergency").move(s);
        }
        
        // Ajustamos el tipo de búsqueda para que salga bonito en la UI
        // (Aunque PlayerMove es inmutable en sus campos básicos, creamos uno nuevo con los datos finales)
        return new PlayerMove(bestMoveSoFar.getPoints(), 
                              bestMoveSoFar.getNumerOfNodesExplored(), 
                              currentDepth - 1, // La profundidad real completada
                              SearchType.MINIMAX_IDS);
    }

    @Override
    public String getName() {
        return "OrtizSerralta (IDS)";
    }
}