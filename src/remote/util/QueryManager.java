/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package remote.util;

import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import modelo.dao.BaseDAO;
import modelo.dto.DataTable;
import remote.Sitio;
import remote.util.InterfaceManager.Interfaces;

/**
 *
 * @author jdosornio
 */
public class QueryManager {

    private static final ThreadLocal<Short> TRANSACTION_OK = new ThreadLocal<>();

    private static volatile short transactionOk;

    /**
     * Inserta los datos de todas las tablas en la interface del sitio elegido.
     *
     * @param savePKs guardar las llaves primarias generadas
     * @param interfaceSitio la interface del sitio al que se desea insertar
     * @param tablas el arreglo de nombres de tablas donde se insertará
     * @param datos el arreglo de DataTables que se desean insertar en el orden
     * en el que están los nombres de las tablas en el arreglo
     *
     * @return 1 en caso de que todo ocurra normalmente, 0 en caso contrario.
     */
    public static short uniInsert(boolean savePKs, Interfaces interfaceSitio,
            String[] tablas,DataTable... datos) {
        short ok = 1;
        try {
            //obtener la interface
            Sitio sitio = InterfaceManager.getInterface(
                    InterfaceManager.getInterfaceServicio(interfaceSitio));

            //insertar los datos
            if (sitio != null) {
                ok = sitio.insert(savePKs, tablas, datos);

                System.out.println("Insert en el sitio: "
                        + interfaceSitio + ", resultado = " + ok);
            }

        } catch (ConnectException ex) {
            Logger.getLogger(QueryManager.class.getName()).log(Level.SEVERE, null, ex);
            ok = 0;
        } catch (RemoteException | NotBoundException ex) {
            Logger.getLogger(QueryManager.class.getName()).log(Level.SEVERE, null, ex);
            ok = 0;
        }

        return ok;
    }

    /**
     * Inserta los datos de todas las tablas en todos los sitios que están
     * registrados en este nodo.
     *
     * @param savePKs guardar las llaves primarias generadas
     * @param tablas el arreglo de nombres de tablas donde se insertará
     * @param datos el arreglo de DataTables que se desean insertar en el orden
     * en el que están los nombres de las tablas en el arreglo
     *
     * @return 1 en caso de que todo ocurra normalmente, 0 en caso contrario.
     * @throws InterruptedException en caso de que ocurra un error con los
     * threads
     */
    public static synchronized short broadInsert(boolean savePKs, String[] tablas,
            DataTable... datos)
            throws InterruptedException {
        List<Thread> hilosInsert = new ArrayList<>();

        //TRANSACTION_OK.set((short)1);
        transactionOk = localInsert(savePKs, tablas, datos);

//        System.out.println("Thread principal solicitante: transacionOk = 1");
//        uniInsert(savePKs, Interfaces.LOCALHOST, tablas, datos);
        System.out.println("savePKs: " + savePKs + " Id: " + datos[0].getValueAt(0, 0));

        //Obtener todas las interfaces de sitio
        for (Interfaces interfaceSitio : Interfaces.values()) {

            if (interfaceSitio.equals(Interfaces.LOCALHOST)) {
                continue;
            }

            Runnable insertar = new Runnable() {
                @Override
                public void run() {
//                    short resultadoTodos = TRANSACTION_OK.get();
//                    System.out.println("Thread de inserción a la interface: " + 
//                            interfaceSitio + ", resultadoTodos = " + resultadoTodos);
//                    System.out.println("Thread de inserción a la interface: " + 
//                            interfaceSitio + ", resultadoTodos = " + transactionOk);

                    short resultadoActual = uniInsert(false, interfaceSitio, tablas, datos);

//                    System.out.println("Thread de inserción a la interface: " + 
//                            interfaceSitio + ", resultadoActual = " + resultadoActual);
                    //short resultadoNuevo = (short)(resultadoTodos * resultadoActual);
                    //TRANSACTION_OK.set(resultadoNuevo);
                    transactionOk *= (short) resultadoActual;

//                    System.out.println("Thread de inserción a la interface: " + 
//                            interfaceSitio + ", resultadoNuevo = " + transactionOk);
                }
            };

            Thread hilo = new Thread(insertar);
            hilo.start();
            hilosInsert.add(hilo);
        }

        for (Thread hilo : hilosInsert) {
            hilo.join();
        }

//        System.out.println("Thread principal solicitante: transactionOk = " + 
//                TRANSACTION_OK.get());
        System.out.println("Thread principal solicitante: transactionOk = "
                + transactionOk);

        return transactionOk;
    }

    public static synchronized short multiInsert(boolean savePKs, String tabla,
            DataTable... datos) throws InterruptedException {
        List<Thread> hilosInsert = new ArrayList<>();

        System.out.println("savePKs: " + savePKs + " Id: " + datos[0].getValueAt(0, 0));

        //Obtener todas las interfaces de sitio
        for (Interfaces interfaceSitio : Interfaces.values()) {            
            
            if (interfaceSitio.equals(Interfaces.LOCALHOST)) {
                continue;
            }

            Runnable insertar = new Runnable() {
                @Override
                public void run() {
                    short resultadoActual = uniInsert(false, interfaceSitio, new String[]{tabla}, datos);
                    transactionOk *= (short) resultadoActual;
                }
            };

            Thread hilo = new Thread(insertar);
            hilo.start();
            hilosInsert.add(hilo);
        }

        for (Thread hilo : hilosInsert) {
            hilo.join();
        }

        System.out.println("Thread principal solicitante: transactionOk = "
                + transactionOk);

        return transactionOk;
    }

    public static short localInsert(boolean savePKs, String[] tablas,
            DataTable... datos) {

        short ok = 1;
        BaseDAO dao = new BaseDAO();
        //Insertar todas las tablas....
        for (int i = 0; i < tablas.length; i++) {
            boolean noError = dao.add(tablas[i], datos[i], savePKs);

            if (!noError) {
                ok = 0;
                break;
            }
        }

        System.out.println("Inserción de " + tablas.length + " tablas, resultado: "
                + ok);

        return ok;
    }
    
}