package agentes;

import dilemaPrisionero.OntologiaDilemaPrisionero;
import dilemaPrisionero.elementos.EntregarJugada;
import dilemaPrisionero.elementos.Jugada;
import dilemaPrisionero.elementos.JugadaEntregada;
import dilemaPrisionero.elementos.ProponerPartida;
import dilemaPrisionero.elementos.ResultadoJugada;
import jade.content.ContentManager;
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.BeanOntologyException;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetResponder;
import jade.proto.ProposeResponder;
import jade.proto.SubscriptionInitiator;
import jade.util.leap.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import juegos.elementos.DetalleInforme;
import juegos.elementos.GanadorPartida;
import juegos.elementos.InformarPartida;
import juegos.elementos.Jugador;
import juegos.elementos.Partida;
import juegos.elementos.PartidaAceptada;
import util.ContenedorPartida;

/**
 *
 * Agente ladron diseñado para comunicarse con el agente policia para jugar distintas partidas
 * 
 * @author jcsp0003
 */
public class AgenteLadronJCSP0003 extends Agent {

    private Map<String, ContenedorPartida> partidasIniciadas;
    private final Codec codec = new SLCodec();

    // La ontología que utilizará el agente
    private Ontology ontologia;

    private Jugador jugador;

    private AID[] agentesConsola;
    private ArrayList<String> mensajesPendientes;

    private final ContentManager manager = (ContentManager) getContentManager();

    private Map<String, InformarPartidaSubscribe> subscribes;

    /**
     * Funcion de inicializacion de las variables e inicio de las tareas basicas
     */
    @Override
    protected void setup() {
        //Inicialización de las variables del agente   
        mensajesPendientes = new ArrayList();
        subscribes = new HashMap<>();
        partidasIniciadas = new HashMap<>();

        DFAgentDescription dfd = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();

        // Regisro de la Ontología
        try {
            ontologia = OntologiaDilemaPrisionero.getInstance();
        } catch (BeanOntologyException ex) {
            Logger.getLogger(AgenteLadronJCSP0003.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            manager.registerLanguage(codec);
            manager.registerOntology(ontologia);
        } catch (Exception e) {
        }

        //registro ontologia
        sd.addOntologies(OntologiaDilemaPrisionero.ONTOLOGY_NAME);

        //registro paginas amarillas
        try {
            sd.setName(OntologiaDilemaPrisionero.REGISTRO_PRISIONERO);
            sd.setType(OntologiaDilemaPrisionero.REGISTRO_PRISIONERO);
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
        }

        mensajesPendientes.add("ME HE CONECTADO A LA PLATAFORMA");

        jugador = new Jugador(this.getLocalName(), this.getAID());

        //BUSCO LA CONSULA Y LE MANDO LOS MENSAJES
        addBehaviour(new TareaBuscarConsola(this, 5000));
        addBehaviour(new TareaEnvioConsola(this, 300));

        //LEO LAS PROPOSICIONES DE PARTIDA
        MessageTemplate plantilla = ProposeResponder.createMessageTemplate(FIPANames.InteractionProtocol.FIPA_PROPOSE);
        addBehaviour(new ProposicionPartida(this, plantilla));

        //Leo las rondas
        MessageTemplate template = ContractNetResponder.createMessageTemplate(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
        addBehaviour(new TareaJugarPartida(this, template));
    }

    /**
     * Funcion llamada al finalizar el agente, encargada de. desregistro de las paginas amarillas
     * y de desuscribrise del policia
     */
    @Override
    protected void takeDown() {
        //Desregristo del agente de las Páginas Amarillas
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
        }

        Iterator<Map.Entry<String, InformarPartidaSubscribe>> entries = subscribes.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, InformarPartidaSubscribe> entry = entries.next();
            entry.getValue().desRegistrarse();
        }

        //Despedida
        System.out.println("Finaliza la ejecución del agente: " + this.getName());
    }

    
    /**
     * Tarea de subscripcion al policia
     */
    private class InformarPartidaSubscribe extends SubscriptionInitiator {

        private AID sender;

        /**
         * Constructor parametrizado
         * @param agente Agente padre
         * @param mensaje Plantilla de mensaje
         */
        public InformarPartidaSubscribe(Agent agente, ACLMessage mensaje) {
            super(agente, mensaje);
        }

        /**
         * Maneja la respuesta en caso que acepte: AGREE
         * @param inform Mensaje de tipo agree
         */
        @Override
        protected void handleAgree(ACLMessage inform) {
            mensajesPendientes.add("Mi subscripcion a la plataforma ha sido aceptada");
            this.sender = inform.getSender();
        }


        /**
         * Maneja la respuesta en caso que rechace: REFUSE
         * @param inform Mensaje de tipo refuse
         */
        @Override
        protected void handleRefuse(ACLMessage inform) {
            mensajesPendientes.add("Mi subscripcion a la plataforma ha sido rechazada");
        }


        /**
         * Maneja la informacion enviada: INFORM
         * @param inform Mensaje de tipo inform
         */
        @Override
        protected void handleInform(ACLMessage inform) {
            mensajesPendientes.add("Me ha llegado un subscribe");

            try {
                DetalleInforme detalle = (DetalleInforme) manager.extractContent(inform);
                if (detalle.getDetalle() instanceof GanadorPartida) {
                    GanadorPartida gp = (GanadorPartida) detalle.getDetalle();
                    mensajesPendientes.add("El ganador de la partida ha sido " + gp.getJugador().getNombre());
                    if (partidasIniciadas.containsKey(detalle.getPartida().getIdPartida())) {
                        ContenedorPartida contenedor = partidasIniciadas.get(detalle.getPartida().getIdPartida());
                        contenedor.mostrarCondenaFinal();
                        contenedor.crearFichero();
                    }
                } else {
                    if (detalle.getDetalle() instanceof juegos.elementos.Error) {
                        juegos.elementos.Error err = (juegos.elementos.Error) detalle.getDetalle();
                        mensajesPendientes.add("Ha habido un error:\n  " + err.getDetalle());
                    }
                }
            } catch (Codec.CodecException | OntologyException ex) {
                Logger.getLogger(AgenteLadronJCSP0003.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        /**
         * Funcion destinada a desregistrarse del policia
         */
        public void desRegistrarse() {
            //Enviamos la cancelación de la suscripcion
            this.cancel(sender, false);

            //Comprobamos que se ha cancelado correctamente
            this.cancellationCompleted(sender);
        }


        /**
         * Maneja la respuesta en caso de fallo: FAILURE
         * @param failure mensaje de tipo failure
         */
        @Override
        protected void handleFailure(ACLMessage failure) {

        }


        @Override
        public void cancellationCompleted(AID agente) {
        }
    }

    
    /**
     * Tarea para la recepcion de propuestas de partida
     */
    private class ProposicionPartida extends ProposeResponder {

        /**
         * Constructor parametrizado
         * @param agente Agente padre
         * @param plantilla Plantilla de mensaje
         */
        public ProposicionPartida(Agent agente, MessageTemplate plantilla) {
            super(agente, plantilla);
        }

        /**
         * Funcion invocada cuando llega una peticion de partida y realiza una subcripcion al policia
         * @param propuesta Mensaje con la propuesta de partida 
         * @return Mensaje afirmativo de jugar
         * @throws NotUnderstoodException 
         */
        @Override
        protected ACLMessage prepareResponse(ACLMessage propuesta) throws NotUnderstoodException {
            ProponerPartida pp = null;
            Partida p = null;
            try {
                Action ac = (Action) manager.extractContent(propuesta);
                pp = (ProponerPartida) ac.getAction();
                p = pp.getPartida();
            } catch (Codec.CodecException | OntologyException ex) {
                Logger.getLogger(AgenteLadronJCSP0003.class.getName()).log(Level.SEVERE, null, ex);
            }

            Jugador j = new Jugador(this.myAgent.getLocalName(), this.myAgent.getAID());
            PartidaAceptada pa = new PartidaAceptada(p, j);

            ACLMessage agree = propuesta.createReply();
            agree.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
            agree.setLanguage(codec.getName());
            agree.setOntology(ontologia.getName());
            try {
                manager.fillContent(agree, pa);
            } catch (Codec.CodecException | OntologyException ex) {
                Logger.getLogger(AgenteLadronJCSP0003.class.getName()).log(Level.SEVERE, null, ex);
            }

            //Almaceno la partida
            ContenedorPartida contenedor = new ContenedorPartida(pp.getCondiciones(), this.myAgent.getLocalName(),mensajesPendientes);
            partidasIniciadas.put(p.getIdPartida(), contenedor);

            //Hacer una suscripcion al policia en caso de no tenerla ya hecha
            if (!subscribes.containsKey(propuesta.getSender().getName())) {
                InformarPartida inf = new InformarPartida(jugador);
                ACLMessage mensaje = new ACLMessage(ACLMessage.SUBSCRIBE);
                mensaje.setProtocol(FIPANames.InteractionProtocol.FIPA_SUBSCRIBE);
                mensaje.setSender(this.myAgent.getAID());
                mensaje.setLanguage(codec.getName());
                mensaje.setOntology(ontologia.getName());
                mensaje.addReceiver(propuesta.getSender());
                try {
                    Action action = new Action(getAID(), inf);
                    manager.fillContent(mensaje, action);
                } catch (Codec.CodecException | OntologyException ex) {
                    Logger.getLogger(AgentePolicia.class.getName()).log(Level.SEVERE, null, ex);
                }

                InformarPartidaSubscribe tarea = new InformarPartidaSubscribe(this.myAgent, mensaje);
                subscribes.put(propuesta.getSender().getName(), tarea);

                addBehaviour(tarea);
            }

            return agree;
        }
    }

    
    /**
     * Tarea para la negociacion de una jugada
     */
    private class TareaJugarPartida extends ContractNetResponder {

        /**
         * Constructor parametrizado
         * @param agente Agente padre
         * @param plantilla Plantillade mensaje
         */
        public TareaJugarPartida(Agent agente, MessageTemplate plantilla) {
            super(agente, plantilla);
        }

        /**
         * Funcion invocada al llegar un mensaje cfp
         * @param cfp Mensaje call for proposal
         * @return Jugada realizada
         * @throws NotUnderstoodException
         * @throws RefuseException 
         */
        @Override
        protected ACLMessage prepareResponse(ACLMessage cfp) throws NotUnderstoodException, RefuseException {
            Action ac;
            EntregarJugada entJug = null;
            List jugadores = null;
            try {
                ac = (Action) manager.extractContent(cfp);
                entJug = (EntregarJugada) ac.getAction();
                jugadores = entJug.getJugadores();
            } catch (Codec.CodecException | OntologyException ex) {
                Logger.getLogger(AgenteLadronJCSP0003.class.getName()).log(Level.SEVERE, null, ex);
            }

            ACLMessage respuesta = cfp.createReply();

            if (partidasIniciadas.containsKey(entJug.getPartida().getIdPartida())) {
                ContenedorPartida contenedor = partidasIniciadas.get(entJug.getPartida().getIdPartida());
                contenedor.insertarRival(jugadores);
                Partida part = entJug.getPartida();
                Jugada jugada = new Jugada(contenedor.decidirAccion());
                JugadaEntregada jugEnt = new JugadaEntregada(part, jugador, jugada);
                respuesta.setPerformative(ACLMessage.PROPOSE);
                respuesta.setSender(myAgent.getAID());
                respuesta.setLanguage(codec.getName());
                respuesta.setOntology(ontologia.getName());

                try {
                    manager.fillContent(respuesta, jugEnt);
                } catch (Codec.CodecException | OntologyException ex) {
                    Logger.getLogger(AgenteLadronJCSP0003.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            return respuesta;
        }

        /**
         * Funcion invocada al llegar el resultado de la ronda
         * @param cfp Mensaje call for proposal
         * @param propose Aceptacion de la partida
         * @param accept Resultado de la partida
         * @return Mensaje inform
         * @throws FailureException 
         */
        @Override
        protected ACLMessage prepareResultNotification(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {  
            ResultadoJugada resultado = null;

            try {
                resultado = (ResultadoJugada) manager.extractContent(accept);
            } catch (Codec.CodecException | OntologyException ex) {
                Logger.getLogger(AgenteLadronJCSP0003.class.getName()).log(Level.SEVERE, null, ex);
            }

            if (partidasIniciadas.containsKey(resultado.getPartida().getIdPartida())) {
                ContenedorPartida contenedor = partidasIniciadas.get(resultado.getPartida().getIdPartida());
                contenedor.nuevaJugadaOponente(resultado);
            }

            ACLMessage inform = accept.createReply();
            inform.setPerformative(ACLMessage.INFORM);
            return inform;
        }

        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            mensajesPendientes.add("Me ha llegado algo al handleRejectProposal");
        }
    }

    /**
     * Tarea que busca agentes consola por donde se mostrarán los mensajes de
     * mensajesPendientes
     */
    public class TareaBuscarConsola extends TickerBehaviour {

        /**
         * constructor para la tarea
         *
         * @param a Agente
         * @param period tiempo
         */
        public TareaBuscarConsola(Agent a, long period) {
            super(a, period);
        }

        /**
         * Funcion encargada de buscar agentes consola
         */
        @Override
        protected void onTick() {
            //Busca agentes consola
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setName(OntologiaDilemaPrisionero.REGISTRO_CONSOLA);
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                if (result.length > 0) {
                    agentesConsola = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        agentesConsola[i] = result[i].getName();
                    }
                } else {
                    agentesConsola = null;
                }
            } catch (FIPAException fe) {
            }
        }
    }

    /**
     * Tarea que se encarga de enviar los mensajes de mensajesPendientes a las
     * consolas encontradas
     */
    public class TareaEnvioConsola extends TickerBehaviour {

        //Tarea de ejemplo que se repite cada 10 segundos
        public TareaEnvioConsola(Agent a, long period) {
            super(a, period);
        }

        /**
         * Funcion encargada de enviar los mensajes pendientes a la consola
         */
        @Override
        protected void onTick() {
            ACLMessage mensaje;
            if (agentesConsola != null) {
                if (!mensajesPendientes.isEmpty()) {
                    mensaje = new ACLMessage(ACLMessage.INFORM);
                    mensaje.setSender(myAgent.getAID());
                    mensaje.addReceiver(agentesConsola[0]);
                    mensaje.setContent(mensajesPendientes.remove(0));

                    myAgent.send(mensaje);
                }
            }
        }
    }
}
