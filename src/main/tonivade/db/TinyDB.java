/*
 * Copyright (c) 2015, Antonio Gabriel Muñoz Conejo <antoniogmc at gmail dot com>
 * Distributed under the terms of the MIT License
 */

package tonivade.db;

import static java.util.Collections.emptyList;
import static tonivade.db.TinyDBConfig.withPersistence;
import static tonivade.db.TinyDBConfig.withoutPersistence;
import static tonivade.db.redis.SafeString.safeAsList;
import static tonivade.db.redis.SafeString.safeString;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import tonivade.db.command.CommandSuite;
import tonivade.db.command.ICommand;
import tonivade.db.command.IRequest;
import tonivade.db.command.IServerContext;
import tonivade.db.command.ISession;
import tonivade.db.command.Request;
import tonivade.db.command.Response;
import tonivade.db.command.Session;
import tonivade.db.data.Database;
import tonivade.db.data.DatabaseValue;
import tonivade.db.data.IDatabase;
import tonivade.db.persistence.PersistenceManager;
import tonivade.db.persistence.RDBInputStream;
import tonivade.db.persistence.RDBOutputStream;
import tonivade.db.redis.RedisArray;
import tonivade.db.redis.RedisToken;
import tonivade.db.redis.RedisToken.IntegerRedisToken;
import tonivade.db.redis.RedisToken.StringRedisToken;
import tonivade.db.redis.RedisTokenType;
import tonivade.db.redis.RequestDecoder;
import tonivade.db.redis.SafeString;

/**
 * Java Redis Implementation
 *
 * @author tomby
 *
 */
public class TinyDB implements ITinyDB, IServerContext {

    private static final Charset DEFAULT_CHATSET = Charset.forName("UTF-8");

    private static final Logger LOGGER = Logger.getLogger(TinyDB.class.getName());

    private static final String SLAVES_KEY = "slaves";

    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final int MAX_FRAME_SIZE = BUFFER_SIZE * 100;

    private final int port;
    private final String host;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private ServerBootstrap bootstrap;

    private TinyDBInitializerHandler acceptHandler;
    private TinyDBConnectionHandler connectionHandler;

    private final Map<String, ISession> clients = new HashMap<>();

    private final List<IDatabase> databases = new ArrayList<IDatabase>();
    private final IDatabase admin = new Database();

    private final CommandSuite commands = new CommandSuite();

    private final BlockingQueue<RedisArray> queue = new LinkedBlockingQueue<>();

    private ChannelFuture future;

    private Optional<PersistenceManager> persistence;

    private boolean master;

    public TinyDB() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public TinyDB(String host, int port) {
        this(host, port, withoutPersistence());
    }

    public TinyDB(String host, int port, TinyDBConfig config) {
        this.host = host;
        this.port = port;
        if (config.isPersistenceActive()) {
            this.persistence = Optional.of(new PersistenceManager(this, config));
        } else {
            this.persistence = Optional.empty();
        }
        for (int i = 0; i < config.getNumDatabases(); i++) {
            this.databases.add(new Database());
        }
    }

    public void start() {
        setMaster(true);

        persistence.ifPresent((p) -> p.start());

        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2);
        acceptHandler = new TinyDBInitializerHandler(this);
        connectionHandler = new TinyDBConnectionHandler(this);

        bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(acceptHandler)
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .option(ChannelOption.SO_RCVBUF, BUFFER_SIZE)
            .option(ChannelOption.SO_SNDBUF, BUFFER_SIZE)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);

        // Bind and start to accept incoming connections.
        future = bootstrap.bind(host, port);

        future.syncUninterruptibly();

        LOGGER.info(() -> "server started: " + host + ":" + port);
    }

    public void stop() {
        try {
            if (future != null) {
                future.channel().close();
            }
            persistence.ifPresent((p) -> p.stop());
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }

        clients.clear();
        queue.clear();
        admin.clear();

        LOGGER.info(() -> "server stopped");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void channel(SocketChannel channel) {
        LOGGER.fine(() -> "new channel: " + sourceKey(channel));

        channel.pipeline().addLast("linDelimiter", new RequestDecoder(MAX_FRAME_SIZE));
        channel.pipeline().addLast(connectionHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connected(ChannelHandlerContext ctx) {
        String sourceKey = sourceKey(ctx.channel());

        LOGGER.fine(() -> "client connected: " + sourceKey);

        getSession(sourceKey, ctx);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnected(ChannelHandlerContext ctx) {
        String sourceKey = sourceKey(ctx.channel());

        LOGGER.fine(() -> "client disconnected: " + sourceKey);

        ISession session = clients.remove(sourceKey);
        if (session != null) {
            cleanSession(session);
        }
    }

    private void cleanSession(ISession session) {
        try {
            processCommand(new Request(this, session, safeString("unsubscribe"), emptyList()));
        } finally {
            session.destroy();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void receive(ChannelHandlerContext ctx, RedisToken message) {
        String sourceKey = sourceKey(ctx.channel());

        LOGGER.finest(() -> "message received: " + sourceKey);

        IRequest request = parseMessage(sourceKey, message, getSession(sourceKey, ctx));
        if (request != null) {
            processCommand(request);
        }
    }

    private ISession getSession(String sourceKey, ChannelHandlerContext ctx) {
        ISession session = clients.getOrDefault(sourceKey, new Session(sourceKey, ctx));
        clients.putIfAbsent(sourceKey, session);
        return session;
    }

    private IRequest parseMessage(String sourceKey, RedisToken message, ISession session) {
        IRequest request = null;
        if (message.getType() == RedisTokenType.ARRAY) {
            request = parseArray(sourceKey, message, session);
        } else if (message.getType() == RedisTokenType.UNKNOWN) {
            request = parseLine(sourceKey, message, session);
        }
        return request;
    }

    private Request parseLine(String sourceKey, RedisToken message, ISession session) {
        String command = message.getValue();
        String[] params = command.split(" ");
        String[] array = new String[params.length - 1];
        System.arraycopy(params, 1, array, 0, array.length);
        return new Request(this, session, safeString(params[0]), safeAsList(array));
    }

    private Request parseArray(String sourceKey, RedisToken message, ISession session) {
        List<SafeString> params = new LinkedList<SafeString>();
        for (RedisToken token : message.<RedisArray>getValue()) {
            if (token.getType() == RedisTokenType.STRING) {
                params.add(token.getValue());
            }
        }
        return new Request(this, session, params.remove(0), params);
    }

    private void processCommand(IRequest request) {
        LOGGER.fine(() -> "received command: " + request);

        ISession session = request.getSession();
        IDatabase db = databases.get(session.getCurrentDB());
        ICommand command = commands.getCommand(request.getCommand());
        if (command != null) {
            if (!isReadOnly(request.getCommand())) {
                session.enqueue(() -> {
                    try {
                        Response response = new Response();
                        command.execute(db, request, response);
                        session.getContext().writeAndFlush(responseToBuffer(session, response));

                        replication(request);

                        if (response.isExit()) {
                            session.getContext().close();
                        }
                    } catch (RuntimeException e) {
                        LOGGER.log(Level.SEVERE, "error executing command: " + request, e);
                    }
                });
            } else {
                session.getContext().writeAndFlush(
                        stringToBuffer(session, "-READONLY You can't write against a read only slave"));
            }
        } else {
            session.getContext().writeAndFlush(
                    stringToBuffer(session, "-ERR unknown command '" + request.getCommand() + "'"));
        }
    }

    private boolean isReadOnly(String command) {
        return !isMaster()
                && !commands.isReadOnlyCommand(command);
    }

    private ByteBuf stringToBuffer(ISession session, String str) {
        byte[] array = DEFAULT_CHATSET.encode(str + "\r\n").array();
        return bytesToBuffer(session, array);
    }

    private ByteBuf responseToBuffer(ISession session, Response response) {
        byte[] array = response.getBytes();
        return bytesToBuffer(session, array);
    }

    private ByteBuf bytesToBuffer(ISession session, byte[] array) {
        ByteBuf buffer = session.getContext().alloc().buffer(array.length);
        buffer.writeBytes(array);
        return buffer;
    }

    private void replication(IRequest request) {
        if (!commands.isReadOnlyCommand(request.getCommand())) {
            RedisArray array = requestToArray(request);
            if (!admin.getOrDefault(SLAVES_KEY, DatabaseValue.EMPTY_SET).<Set<String>>getValue().isEmpty()) {
                queue.add(array);
            }
            persistence.ifPresent((p) -> p.append(array));
        }
    }

    private RedisArray requestToArray(IRequest request) {
        RedisArray array = new RedisArray();
        // currentDB
        array.add(new IntegerRedisToken(request.getSession().getCurrentDB()));
        // command
        array.add(new StringRedisToken(safeString(request.getCommand())));
        //params
        for (SafeString safeStr : request.getParams()) {
            array.add(new StringRedisToken(safeStr));
        }
        return array;
    }

    private String sourceKey(Channel channel) {
        InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
        return remoteAddress.getHostName() + ":" + remoteAddress.getPort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(String sourceKey, String message) {
        ISession session = clients.get(sourceKey);
        if (session != null) {
            SafeString safeString = safeString(message);
            ByteBuf buffer = session.getContext().alloc().buffer(safeString.length());
            buffer.writeBytes(safeString.getBuffer());
            session.getContext().writeAndFlush(buffer);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDatabase getAdminDatabase() {
        return admin;
    }

    @Override
    public IDatabase getDatabase(int i) {
        return databases.get(i);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPort() {
        return port;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getClients() {
        return clients.size();
    }

    @Override
    public List<RedisArray> getCommands() {
        List<RedisArray> current = new LinkedList<>();
        queue.drainTo(current);
        return current;
    }

    @Override
    public ICommand getCommand(String name) {
        return commands.getCommand(name);
    }

    @Override
    public void exportRDB(OutputStream output) throws IOException {
        RDBOutputStream rdb = new RDBOutputStream(output);
        rdb.preamble(6);
        for (int i = 0; i < databases.size(); i++) {
            IDatabase db = databases.get(i);
            if (!db.isEmpty()) {
                rdb.select(i);
                rdb.dabatase(db);
            }
        }
        rdb.end();
    }

    @Override
    public void importRDB(InputStream input) throws IOException {
        RDBInputStream rdb = new RDBInputStream(input);

        for (Entry<Integer, IDatabase> entry : rdb.parse().entrySet()) {
            this.databases.set(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean isMaster() {
        return master;
    }

    @Override
    public void setMaster(boolean master) {
        this.master = master;
    }

    public static void main(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
        OptionSpec<Void> help = parser.accepts("help", "print help");
        OptionSpec<Void> persist = parser.accepts("P", "with persistence");
        OptionSpec<String> host = parser.accepts("h", "host").withRequiredArg().ofType(String.class).defaultsTo(DEFAULT_HOST);
        OptionSpec<Integer> port = parser.accepts("p", "port").withRequiredArg().ofType(Integer.class).defaultsTo(DEFAULT_PORT);

        OptionSet options = parser.parse(args);

        if (options.has(help)) {
            parser.printHelpOn(System.out);
        } else {
            String optionHost = options.valueOf(host);
            int optionPort = parsePort(options.valueOf(port));
            TinyDBConfig optionPesistence = parseConfig(options.has(persist));
            TinyDB db = new TinyDB(optionHost, optionPort, optionPesistence);
            db.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> db.stop()));
        }
    }

    private static int parsePort(Integer optionPort) {
        return optionPort != null ? optionPort : DEFAULT_PORT;
    }

    private static TinyDBConfig parseConfig(boolean persist) {
        TinyDBConfig config = null;
        if (persist) {
            config = withPersistence();
        } else {
            config = withoutPersistence();
        }
        return config;
    }

}
