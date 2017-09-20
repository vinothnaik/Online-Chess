
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

class Timer extends Thread
{
	private ChessServer server;
	public Timer(ChessServer s) {
		this.server = s;
	}
	
	public void run(){
		try {
			Thread.sleep(60000);//it makes the client to wait for 1 minute to check for available users
		} catch (Exception e) {
		System.out.println(e);
		}
		server.timeUp();//this wakes up thread and checks for list of available players
	}
}


class Locker 
{
	private Hashtable<String, ChessServer> availableUsers;

	public Locker(Hashtable<String, ChessServer> w){
		this.availableUsers = w;
	}
	
	public synchronized boolean addPlayer(String playerName, ChessServer server){
		
		if ( availableUsers.containsKey(playerName)){
			return false;//if the player name is already available then new player is not added
		}else{
			availableUsers.put(playerName, server);
			return true;
		}
	}
	
	//for removing player from list of available players
	public synchronized ChessServer deleteUser(String playerName){
		ChessServer server = availableUsers.get(playerName);
		availableUsers.remove(playerName);
		return server;
	}
	
	//to get player name
	public ChessServer getUser(String playerName){
		return availableUsers.get(playerName);
	}
	
	//returns player name
	public synchronized boolean contains(String playerName) 
	{
		return availableUsers.containsKey(playerName);
	}
	
	public int size(){
		return availableUsers.size();
	}
	
	public Enumeration<String> keys(){
		return availableUsers.keys();
	}
}



class ChessServer extends Thread{
	private Socket client_Socket;
	private PrintWriter out;
	private BufferedReader in;
	private Locker lockAvailableUsers;
	private String playerName = "";
	private boolean rendezvous = false;
	private ChessServer opponentPlayer = null;
	private String reply = "";
	private boolean timeUp = false;
	private String my_color = "";
	private String opponent_Color = "";
	private chessBoard board_manager = null;
	private boolean finish = false;
	private boolean my_turn = true;

	
	public ChessServer(Socket socket, Locker lock)
	{
		client_Socket = socket;
		lockAvailableUsers = lock;
		
		
		try 
		{
			out = new PrintWriter(client_Socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(client_Socket.getInputStream()));
		} 
		catch (IOException e) 
		{
			System.out.println(e);
		}
		
	}
	//to get player name
	public String getPlayerName()
	{
		return this.playerName;
	}
	
	//to notify time up user
	public synchronized void timeUp()
	{
		timeUp = true;
		notify();//this awakes the sleeping thread
	}
	
	public chessBoard getBoard()
	{
		return this.board_manager;
	}
	
	//waiting for players to login
	public synchronized boolean waitForOtherPlayer()
	{
		this.timeUp = false;
		new Timer(this).start();
		
		while(opponentPlayer == null && !timeUp){
			try {
				wait();
			} 
			catch (Exception e) 
			{
				System.out.println(e);
			}
		}
		return opponentPlayer!=null;
	}
	
	public synchronized void stopToWait(ChessServer server)
	{

		if (opponentPlayer == null){
			opponentPlayer = server;
			notify();
		}
	}
	
	public synchronized void awaitingReply()
	{
		timeUp = false;
		new Timer(this).start();
		while(reply.equals("") && !timeUp)
		{
			try 
			{
				wait();
			} 
			catch (Exception e) 
			{
				System.out.println(e);
			}
		}
	}
	
	public synchronized void reply(String reply)
	{
		this.reply = reply;
		notify();
	}
	
	private void chooseUser()
	{
		try {
			String user;
			
			while ( (user = in.readLine()) != null )
			{
				if ( !user.equals("") && !Pattern.matches("\\s+", user) && user.indexOf("&")<0 && !user.equals("wait") &&
						lockAvailableUsers.addPlayer(user, this))
				{
					break;
				}
				else
				{
					out.println("should_not_username");
				}
			}
			this.playerName = user;
		}
		catch (Exception e) 
		{
			System.out.println(e);
		}
	}
	
	public boolean isRendezving(){
		return this.rendezvous;
	}
	
	//accepting or rejecting invitation sent by other player
	private boolean sendReply()
	{
		rendezvous = true;
		out.println("send_play_request");
		out.println(opponentPlayer.getPlayerName());
		try {
			String reply;
			while ( (reply = in.readLine()) != null )
			{
				if (reply.equals("y")){
					opponentPlayer.reply("y");
					return true;
				}else if (reply.equals("n")){
					opponentPlayer.reply("n");
					opponentPlayer = null;
					rendezvous = false;
					return false;
				}
			}
		}
		catch (Exception e) 
		{
			System.out.println(e);
		}
		return false;
		
	}
	
	public synchronized String sendMyColor(String color)
	{
		if (this.my_color.equals(""))
		{
			board_manager = new chessBoard();
			board_manager.addPlayer(this.playerName);
		}
		while (this.my_color.equals(""))
		{
			try {
				wait();
			} 
			
			catch (Exception e) {
				System.out.println(e);
			}
		}
		this.opponent_Color = color;
		return my_color;
		
		
	}
	
	private synchronized void setMyColor(String color)
	{
		this.my_color = color;
		notify();
	}

	//sending board status to opponent player
	private void send_chessBoard()
	{
		String[][] chessBoard;
		if (this.my_color.equals("white"))
			chessBoard = board_manager.get_white_board();//getting white coin board
		else
			chessBoard = board_manager.get_black_board();//getting black coin board
		out.println("chessBoard");
		for (int i = 0 ; i < 8; i ++)
		{
			for(int j = 0; j < 8; j++)
			{
				if ( chessBoard[i][j] == null)
					out.println("-");
				else
					out.println(chessBoard[i][j]);//getting the coins in non blank places
			}
		}
		out.println(board_manager.getTime(this.my_color.equals("black")));
	}
	
	
	private synchronized void wait_4my_turn()
	{
		while(!my_turn)
		{
			try 
			{
				wait();
			} 
			
			catch (Exception e) 
			{
				System.out.println(e);
			}
		}
	}
	
	private synchronized void now_you_can_play()
	{
		my_turn = true;
		notify();
	}
	//selecting the opponent player out of all available players
	public void run() 
	{
		String message = "";
		out.println("login_username");//this message is sent to the client and client follows steps based on these messages

		try {
			
			chooseUser();
			boolean accept = false;
			
			while (!accept){
			
				if (lockAvailableUsers.size() == 1)
				{
					out.println("players_to_play");
					if (waitForOtherPlayer())
						accept = sendReply();

				
				}
				else
				{
									
					if (opponentPlayer != null)
					{
						accept = sendReply();
					
					
					}
					else
					{
						out.println("list_of_players");
						for(Enumeration<String> enum1 = lockAvailableUsers.keys(); enum1.hasMoreElements();)
						{
							String next = enum1.nextElement();
							if ( !next.equals(this.playerName))
								out.println(next);
					    }
						out.println("**");
						
						String selectedPlayer = "";
						if ( (selectedPlayer = in.readLine()) != null ){
							if ( selectedPlayer.equals("wait")){
								if (waitForOtherPlayer())
									accept = sendReply();
								
							}else if ( !selectedPlayer.equals("") && !Pattern.matches("\\s+", selectedPlayer) && 
					    			lockAvailableUsers.contains(selectedPlayer) && !selectedPlayer.equals(this.playerName)){
								
					    		opponentPlayer = lockAvailableUsers.getUser(selectedPlayer);
							    if (opponentPlayer != null && !opponentPlayer.isRendezving()){
							    	
							    	opponentPlayer.stopToWait(this);
							    	awaitingReply();
							    	
							    	if (this.reply.equals("y"))
									{
							    		out.println("y");
							    		lockAvailableUsers.deleteUser(this.playerName);
							    		lockAvailableUsers.deleteUser(opponentPlayer.getPlayerName());
								    	accept = true;
								    
							    	}
									else if (this.reply.equals(""))
									{
							    		out.println("noreply_given");
							    		opponentPlayer = null;
							    		
							    	}
									else
									{
							    		out.println("n");
							    		opponentPlayer = null;
							    		this.reply = "";
							    		
							    	}
		
							    }
								else 
								{
							    	out.println("noreply_given");
							    	opponentPlayer = null;
							    }
					    	}
							else
							{
					    		out.println("invalid_player_chosen");
					    	}
						}
					}

				}
			}
			
			
			out.println("color");
			String color;
			while ( (color = in.readLine()) != null )
			{
				setMyColor(color);
				opponent_Color = opponentPlayer.sendMyColor(this.my_color);
				if (this.board_manager == null)
				{
					board_manager = opponentPlayer.getBoard();
					board_manager.addPlayer(this.playerName);
				}
							
				
					if (my_color.equals("white") && opponent_Color.equals("white") ||
					my_color.equals("black") && opponent_Color.equals("black"))
					{
					System.out.println("Both the players trying to choose same color of coins.Please restart!!!");
					my_color = "";
					opponent_Color = "";
					this.board_manager = null;
					
				
				}
				else if ((my_color.equals("white") && opponent_Color.equals("black")) ||
						  (my_color.equals("black") && opponent_Color.equals("white")) ||
						  (my_color.equals("white") && opponent_Color.equals("tac")) || 
						  (my_color.equals("black") && opponent_Color.equals("tac")) ){
					if (my_color.equals("white")){
						board_manager.setPlayer1(playerName);
					}else{
						board_manager.setPlayer2(playerName);
					}
					System.out.println("You can start the game...");
					break;
					
				
				}else if (my_color.equals("tac") && opponent_Color.equals("white")){
					this.my_color = "black";
					board_manager.setPlayer2(playerName);
					out.println("black");
					break;
					
				
				}else if (my_color.equals("tac") && opponent_Color.equals("black")) {
					this.my_color = "white";
					board_manager.setPlayer1(playerName);
					out.println("white");
					break;
				
				
				}else if (my_color.equals("tac") && opponent_Color.equals("tac")) {
					if (board_manager.imWhite(playerName)){
						this.my_color = "white";
						out.println("white");
					}else{
						this.my_color = "black";
						out.println("black");
					}
					break;
				}
				
			}
			out.println("init");
			if (this.my_color.equals("black"))
				my_turn = false;

			send_chessBoard();
			
			
			while (!finish){
				
				if (my_turn && board_manager.win(this.my_color.equals("black")) == -1){
					out.println("move");
					String at = "";
					String to = "";
					String time = "";
					while ((at = in.readLine()) != null && (to = in.readLine()) != null && (time = in.readLine()) != null)
					{
						board_manager.setTime(this.my_color.equals("black"), time);
						if (board_manager.is_valid_move(at, to, this.my_color.equals("black")))
						{
							if (board_manager.is_pawn_promoted())
							{
								out.println("pawn_promoted");
								String piece;
								if((piece = in.readLine()) != null)
								{
									board_manager.makePromotion(piece, this.my_color.equals("black"));
								}
							}
							send_chessBoard();
							
							my_turn = false;
							opponentPlayer.now_you_can_play();
							
							if (board_manager.win(this.my_color.equals("black")) == 0)
							{
								out.println("win");
								finish = true;
							}
							break;
						}
						else
						{
							out.println("invalidMove");
						}
					}

				}
				else if (my_turn && board_manager.win(this.my_color.equals("black")) == 1)
				{
					out.println("lose");
					finish = true;
				}
				else
				{
					wait_4my_turn();
					send_chessBoard();
									
				}
			}
		System.out.println("Game Over !!!");
			
		} 
		catch (IOException e) 
		{
		 e.printStackTrace();
		}
		
    }
}


public class OnlineChess 
{
	private static int port;
	private static String server_IP_Address = "";
	private static PrintWriter out;
	private static BufferedReader stdIn;
	private static long total_time = 0;

	private static void ChessServer(){
		ServerSocket serverSocket;

		try {
			Locker lockAvailableUsers = new Locker(new Hashtable<String, ChessServer>());//it creates  list of available users
			serverSocket = new ServerSocket(port);// it initiates ServerSocket on the given port,which is given as part of command line arguments

			//initiating ChessServer,by creating object of ChessServer class,with Server Socker and available users
			while(true){
				ChessServer server = new ChessServer(serverSocket.accept(), lockAvailableUsers);
				server.start();//server thread is initatiated
			}
			    
		} catch (IOException e) {
			System.out.println(e);
			
		}
	}
	
	private static String chooseColor()
	{
		System.out.println("Choose a color for coins- enter black, white or tac (toss a coin)");
		String reply = "";
		try {
			while ( (reply = stdIn.readLine()) != null )
			{
				if (reply.equals("white") || reply.equals("black") || 
					reply.equals("tac"))
				{
					out.println(reply);
					break;
				}
				else
				{
					System.out.println("Invalid reply. Choose a color: black, white or tac (toss a coin)");
				}
			}
		} 
		catch (IOException e) 
		{
			System.out.println(e);
		}
		return reply;
	}
	
	private static void getMove(){
		String at = "";
    	String to = "";
    	long start_time, end_time;
    	start_time = System.currentTimeMillis();
    	System.out.print("Your turn-Please enter Row & Column numbers coin which is to be moved :");
    	try {
			if ( (at = stdIn.readLine()) != null ){
				System.out.print("Please enter Row & Column numbers where this coin is to be moved :");
				if ( (to = stdIn.readLine()) != null ){
					out.println(at);
					out.println(to);
					end_time = System.currentTimeMillis();
			    	total_time += end_time - start_time;
			    	out.println(time(total_time));
			    	System.out.println();
				}
			}
    	} 
		catch (IOException e) 
		{
			System.out.println(e);
		}
	}
	
	private static void choosPiece()
	{
		String reply = "";
		try {
			while ( (reply = stdIn.readLine()) != null ){
				if (reply.equals("queen") || reply.equals("rook") || 
					reply.equals("bishop") || reply.equals("knight")){
					out.println(reply);
					break;
				}else{
					System.out.println("Invalid reply");
					System.out.println("Pawn Promoted.What you prefer pawn to promote to 'queen','rook', 'bishop', or 'knight' :");
				}
			}
		} 
		catch (IOException e) 
		{
			System.out.println(e);
		}
		
	}
	
	private static String time(long time)
	{
		if (time == 0)
			return "(00:00:00)";
		
		long hours =  (time / 3600000);
		long temp1 = (time % 3600000);
		
		long minutes =  (temp1 / 60000);
		temp1 = (temp1 % 60000);
		
		long seconds =  (temp1 / 1000);
	
		
		return "(" + hours + ":" + minutes + ":" + seconds + ")";
		
	}
	
	private static void Client()
	{
		
		Socket echo_Socket;
		try {
			echo_Socket = new Socket(server_IP_Address, port);
			out = new PrintWriter(echo_Socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader( new InputStreamReader(echo_Socket.getInputStream()));
			stdIn = new BufferedReader(new InputStreamReader(System.in));
			String server_messagge, client_Message;
			String opponent = "";
			String playerName = "";
			String color = "";
			String otherColor = "";
			
			while ((server_messagge = in.readLine()) != null) {
				
				
			    if (server_messagge.equals("login_username")){
			    	System.out.println("Welcome to online chess game. Please enter your username: ");
			    	client_Message = stdIn.readLine();
			    	playerName = client_Message;
			    	out.println(client_Message);
			    	
			    
			    }else if (server_messagge.equals("should_not_username")){
			    	System.out.println("User name already exists/Invalid username. Please enter your username : ");
			    	client_Message = stdIn.readLine();
			    	playerName = client_Message;
			    	out.println(client_Message);
			    	
			    
			    } 
				else if (server_messagge.equals("players_to_play"))
				{
			    	System.out.println("There is no players availale to play.Please wait for players to join Online Chess game...");
			    
			    
			    }
				//list of available players
				else if (server_messagge.equals("list_of_players")){
			    	String listOfPlayers = "";
			    	String player = "";
			    	while ((player = in.readLine()) != null && !player.equals("**")){
			    		listOfPlayers += player + "\n";
			    	}
			    	System.out.println("Choose one of the available players:");
			    	System.out.print(listOfPlayers);
			    	System.out.println("If you prefer to wait then send 'wait'");
			    	client_Message = stdIn.readLine();
			    	opponent = client_Message;
			    	out.println(client_Message);
			    	
			    
			    }
				else if (server_messagge.equals("invalid_player_chosen"))
				{
			    	System.out.println("Invalid reply.");
			    
			    }
				//the other player accepts the invitation 
				else if (server_messagge.equals("y")){
			    	System.out.println(opponent+" accept your invitation");
			    	
			    // The other player rejects the invitation
			    }else if (server_messagge.equals("n")){
			    	System.out.println(opponent+" rejects your invitation");
			    
			    
			    }else if (server_messagge.equals("send_play_request")){
			    	
			    	if ((opponent = in.readLine()) != null){
			    		System.out.println(opponent + " wants to play with you.Press y to accept OR press n to decline :");
			    		
			    		String reply = "";
			    		while ( (reply = stdIn.readLine()) != null ){
							if (reply.equals("y") || reply.equals("n")){
								out.println(reply);
								break;

							}else{
								System.out.println("Invalid reply. " + opponent + " wants to play with you.Press y to accept OR press n to decline : ");
							}
			    		}
			    	}
			    	
			    
			    }
				
				else if (server_messagge.equals("noreply_given"))
				{
			    	System.out.println(opponent+" doesn't reply");
			    
			    
			    }
				else if (server_messagge.equals("color"))
				{
			    	color = chooseColor();
			    	
			    
			    }
				else if (server_messagge.equals("samecolor"))
				{
			    	System.out.println("Both players trying to choose the same same color of coins !!!");
			    	color = chooseColor();
			    
			    }
				else if (server_messagge.equals("black") || server_messagge.equals("white"))
				{
			    	System.out.println("Your color is "+server_messagge);
			    	color = server_messagge;
	
			    }
				else if (server_messagge.equals("init")){
			    	if (color.equals("white")){
			    		color = "WHITE";
			    		otherColor = "black";
			    	}else{
			    		color = "black";
			    		otherColor = "WHITE";
			    	}
			    	
			    	System.out.println("Game started...");
			    	System.out.println("For castling at any stage, please write: 'castling short' or 'castling long' ");
			    	
			    
			    }
				else if (server_messagge.equals("chessBoard"))
				{
			    	
			    	String aux = "";
			    	String chessBoard = ""; 
			    	String timeOther = "";
			    	for (int i = 0 ; i < 8; i ++){
						for(int j = 0; j < 8; j++){
							if ((aux = in.readLine()) != null){
								chessBoard += aux + " ";
							}
						}
						chessBoard += "\n";
					}
			    	if ((timeOther = in.readLine()) != null)
				    	System.out.println(otherColor+ " " + timeOther +"\n");
			    	else
			    		System.out.println(otherColor +"\n");
			    	System.out.println(chessBoard);
			    	System.out.println(color + " " + time(total_time)+"\n");
			    	
			    }
				else if (server_messagge.equals("move"))
				{
			    	
			    	getMove();
			    	
			    }
				else if (server_messagge.equals("invalid Move\n"))
				{
			    	System.out.println("You can not proceed with this invalid move !!!!");
					System.out.println("Please do re attempt :");
			    	getMove();
			    	
			    }else if (server_messagge.equals("pawn_promoted")){
			    	System.out.println("Pawn Promoted.What you prefer pawn to promote to 'queen','rook', 'bishop', or 'knight' :");
			    	choosPiece();
			    	
			    	
			    }else if (server_messagge.equals("win")){
			    	System.out.println("GAME OVER!!!");
					System.out.println("CONGRATULATIONS......YOU WON THE GAME!!!");
			    	break;
			    	
			    }else if (server_messagge.equals("lose")){
			    	System.out.println("GAME OVER!!!");
					System.out.println("You lost the game.Better luck Next time !!!");
			    	break;
			    }
			    
			}
			
			
		} catch (IOException e) {
			System.out.println(e);
		} catch (Exception e) {
			System.out.println(e);
		}
	}
	
	public static void main(String[] args){
		
		
		
		//Server Mode
		if (args.length == 1){
			port = Integer.parseInt(args[0]);
			
		// Client Mode
		}else if (args.length == 2){
			server_IP_Address = args[0];
			port = Integer.parseInt(args[1]);
			
		
		}else{
			
			System.out.println("To initiate ChessServer, please enter 'java OnlineChess &port");
			System.out.println("TO initiate Client mode, please enter 'java OnlineChess &serverIP &serverPort");
			System.exit(0);
		}
		//checking for validity of port number
		if (port < 1 || port > 65535){
			System.out.println("port number should be between 1 and 65535.Please try again !!!");
			System.exit(0);
		}
		
		/* This considers current program as Server mode and server IP will be the IP of the comuter on which this is being run*/
		if ( server_IP_Address.equals("") ){
			ChessServer();
		}
		/* Else the current program will be considered as client, which will be connected to server*/
		else
		{
			Client();
		}
		
	}
}


class chessBoard {
	private String[][] chessBoard = new String[8][8];
	private String player1 = "";
	private String player2 = "";
	private boolean toss_coin = false;
	private boolean white_castling_left = true,white_castling_right = true,black_castling_left = true,black_castling_right = true;
	private boolean pawn_promoted = false;
	private int promotion_row,promotion_col;
	private boolean white_Check = false,black_Check = false;
	
	private int threatening_row = -1,threatening_col = -1;
	private int white_king_row = 7,white_king_col = 4;
	private int black_king_row = 0,black_king_col = 4;
	
	private int white_check_mate = -1,black_check_mate = -1;
	
	private String whiteTime = "(00:00:00)";
	private String blackTime = "(00:00:00)";

	public chessBoard(){
		initBoard();
	}
	
	public String getTime(boolean isBlack){
		if (!isBlack)
			return blackTime;
		else
			return whiteTime;
	}
	
	public void setTime(boolean isBlack, String time){
		if (!isBlack)
			whiteTime = time;
		else
			blackTime = time;
	}
	
	public boolean is_pawn_promoted(){
		if (! pawn_promoted)
			return false;
		else
		{			
		pawn_promoted = false;
			return true;
		}
	}

	public int win(boolean isBlack){
		if (isBlack)
			return black_check_mate;
		else
			return white_check_mate;
	}
	public void makePromotion(String coin, boolean isBlack){
		if (!isBlack ){
			if (coin.equals("queen")||coin.equals("Queen")){
				chessBoard[promotion_row][promotion_col] = "Q";
				check_queen_movement(promotion_row,promotion_col,isBlack);
			}else if (coin.equals("rook")||coin.equals("Rook")){
				chessBoard[promotion_row][promotion_col] = "R";
				check_rook_movement(promotion_row,promotion_col,isBlack);
			}else if (coin.equals("bishop")||coin.equals("Bishop")){
				chessBoard[promotion_row][promotion_col] = "B";
				check_bishop_movement(promotion_row,promotion_col,isBlack);
			}else if (coin.equals("knight")||coin.equals("Knight")){
				chessBoard[promotion_row][promotion_col] = "N";
				check_kniight_movement(promotion_row,promotion_col,isBlack);
			}
		}else{
			if (coin.equals("queen")||coin.equals("Queen")){
				chessBoard[promotion_row][promotion_col] = "q";
				check_queen_movement(promotion_row,promotion_col,isBlack);
			}else if (coin.equals("rook")||coin.equals("Rook")){
				chessBoard[promotion_row][promotion_col] = "r";
				check_rook_movement(promotion_row,promotion_col,isBlack);
			}else if (coin.equals("bishop")||coin.equals("Bishop")){
				chessBoard[promotion_row][promotion_col] = "b";
				check_bishop_movement(promotion_row,promotion_col,isBlack);
			}else if (coin.equals("knight")||coin.equals("Knight")){
				chessBoard[promotion_row][promotion_col] = "n";
				check_kniight_movement(promotion_row,promotion_col,isBlack);
			}
		}
	}
	
	public void addPlayer(String user){
		if (player1.equals("")){
			player1 = user;
		}else{
			player2 = user;
		}
	}
	
	public boolean isPlayer1(String user){
		return player1.equals(user);
	}
	
	public synchronized boolean imWhite(String user){
		if (!toss_coin){
			Random rnd = new Random();
			rnd.setSeed(System.currentTimeMillis()); 
			Float coin = rnd.nextFloat();
			if (coin > 0.5){
				String aux = player1;
				player1 = player2;
				player2 = aux;
			}
			toss_coin = true;
		}
		return user.equals(player1);
	}
	
	public void setPlayer1(String user){
		player1 = user;
	}
	
	public void setPlayer2(String user){
		player2 = user;
	}
	
	private void initBoard(){
		
		for (int i = 0 ; i < 8 ; i ++ ){
			chessBoard[1][i] = "p";
			chessBoard[6][i] = "P";
		}
		
		chessBoard[0][4] = "k";
		chessBoard[7][4] = "K";//for king
		
		chessBoard[0][3] = "q";
		chessBoard[7][3] = "Q";//for Queen		
	
		chessBoard[0][2] = chessBoard[0][5] = "b";
		chessBoard[7][2] = chessBoard[7][5] = "B";//for bishop		
		
		chessBoard[0][1] = chessBoard[0][6] = "n";
		chessBoard[7][1] = chessBoard[7][6] = "N";//for knight
	
		chessBoard[0][0] = chessBoard[0][7] = "r";
		chessBoard[7][0] = chessBoard[7][7] = "R";//for rook
			
	}
	
	public String[][] get_white_board(){
		return chessBoard;
	}
	
	public String[][] get_black_board(){
		String[][] temp_board = new String[8][8];
		for (int i = 0 ; i < 8; i ++){
			for(int j = 0; j < 8; j++){
				temp_board[i][j] = chessBoard[8-i-1][8-j-1];
			}
		}
		
		return temp_board;
	}
	
	
	private boolean valid_rook_movement(int row1, int col1, int row2, int col2){
		if ( row1 != row2 && col1 != col2){
			return false;
			
		}else if (row1 == row2 ){
			int min_col_num = Math.min(col1,col2);
			int max_col_num = Math.max(col1,col2);
			
			for(int j = min_col_num + 1 ; j < max_col_num ; j++){
				if ( chessBoard[row1][j] != null )
					return false;
			}
			
		}else if (col1 == col2){
			int min_row_num = Math.min(row1,row2);
			int max_row_num = Math.max(row1, row2);
			for(int i = min_row_num + 1; i < max_row_num ; i++){
				if ( chessBoard[i][col1] != null)
					return false;
			}
		}
		return true;
	}
	
	private void check_rook_movement(int row2, int col2, boolean isBlack){
		boolean isCheck = false;
		int i = row2 - 1;
		
		// for upward movement
		while ( !isCheck && i >= 0 ){
			if (!isBlack && chessBoard[i][col2]!=null && chessBoard[i][col2].equals("k")){
				isCheck = true;
				white_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}if (isBlack  && chessBoard[i][col2]!=null && chessBoard[i][col2].equals("K")){
				isCheck = true;
				black_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}else if (chessBoard[i][col2]!=null && !chessBoard[i][col2].equals("k"))
				break;
			
			i--;
		}
		
		// for downward movement
		i = row2 + 1;
		while ( !isCheck && i < 8 ){
			if (!isBlack && chessBoard[i][col2]!=null && chessBoard[i][col2].equals("k")){
				isCheck = true;
				white_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}if (isBlack  && chessBoard[i][col2]!=null && chessBoard[i][col2].equals("K")){
				isCheck = true;
				black_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}else if (chessBoard[i][col2]!=null && !chessBoard[i][col2].equals("k"))
				break;
			i++;
		}
		
		// for left movement
		i = col2 - 1;
		while ( !isCheck && i >= 0 ){
			if (!isBlack && chessBoard[row2][i]!=null && chessBoard[row2][i].equals("k")){
				isCheck = true;
				white_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}if (isBlack  && chessBoard[row2][i]!=null && chessBoard[row2][i].equals("K")){
				isCheck = true;
				black_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}else if (chessBoard[row2][i]!=null && !chessBoard[row2][i].equals("k"))
				break;
			i--;
		}
		
		// for right movement
		i = col2 + 1;
		while ( !isCheck && i < 8 ){
			if (!isBlack && chessBoard[row2][i]!=null && chessBoard[row2][i].equals("k")){
				isCheck = true;
				white_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}if (isBlack  && chessBoard[row2][i]!=null && chessBoard[row2][i].equals("K")){
				isCheck = true;
				black_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}else if (chessBoard[row2][i]!=null && !chessBoard[row2][i].equals("k"))
					break;
			i--;
		}
	}
	
	private boolean valid_bishop_movement(int row1, int col1, int row2, int col2){
		int row_abs = Math.abs(row2 - row1);
		int col_abs = Math.abs(col2 - col1);
		if ( row_abs != col_abs )
			return false;
		
		int t_row1 = row1;
		int t_col1 = col1;

		for ( int i = 0; i < row_abs - 1 ; i++ ){
			if (row1 < row2 )
				t_row1++;
			else
				t_row1--;
			
			if ( col1 < col2 )
				t_col1++;
			else
				t_col1--;
			
			if (chessBoard[t_row1][t_col1] != null)
				return false;
		}
		return true;
	}
	
	private void check_bishop_movement(int row2, int col2, boolean isBlack){
		int i = row2 - 1 ;
		int j = col2 - 1;
		boolean isCheck = false;
		
		
		while (!isCheck && i >= 0 && j >= 0)//for diagonal upleft
		{
			if (!isBlack && chessBoard[i][j]!=null && chessBoard[i][j].equals("k")){
				isCheck = true;
				white_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}else if (isBlack && chessBoard[i][j]!=null && chessBoard[i][j].equals("K")){
				isCheck = true;
				black_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}else if (chessBoard[i][j]!=null && chessBoard[i][j].equals("k"))
				break;
			i--;
			j--;
		}
		
		i = row2 - 1;
		j = col2 + 1;
		
		while (!isCheck && i >= 0 && j < 8)//for diagonal up right
		{
			if (!isBlack && chessBoard[i][j]!=null && chessBoard[i][j].equals("k")){
				isCheck = true;
				white_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}else if (isBlack && chessBoard[i][j]!=null && chessBoard[i][j].equals("K")){
				isCheck = true;
				black_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}else if (chessBoard[i][j]!=null && chessBoard[i][j].equals("k"))
				break;
			i--;
			j++;
		}
		
		i = row2 + 1;
		j = col2 + 1;
		
		while (!isCheck && i < 8 && j < 8)//for diagonal down right
		{
			if (!isBlack && chessBoard[i][j]!=null && chessBoard[i][j].equals("k")){
				isCheck = true;
				white_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}else if (isBlack && chessBoard[i][j]!=null && chessBoard[i][j].equals("K")){
				isCheck = true;
				black_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}else if (chessBoard[i][j]!=null && chessBoard[i][j].equals("k"))
				break;
			i++;
			j++;
		}
		
		i = row2 + 1;
		j = col2 - 1;
		
		while (!isCheck && i < 8 && j >= 0)//for diagonal down left
		{
			if (!isBlack && chessBoard[i][j]!=null && chessBoard[i][j].equals("k")){
				isCheck = true;
				white_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}else if (isBlack && chessBoard[i][j]!=null && chessBoard[i][j].equals("K")){
				isCheck = true;
				black_Check = true;
				threatening_row = row2;
				threatening_col = col2;
			}else if (chessBoard[i][j]!=null && chessBoard[i][j].equals("k"))
				break;
			i++;
			j--;
		}
		
	}
	
	private boolean valid_queen_movement(int row1, int col1, int row2, int col2){
		
		
		if (row1 == row2 )// for horizontal movement
		{
			int min_col = Math.min(col1,col2);
			int max_col = Math.max(col1,col2);
			
			for(int j = min_col + 1 ; j < max_col ; j++)
			{
				if ( chessBoard[row1][j] != null )
					return false;
			}
		
		}
		else if (col1 == col2)// for vertical movement
		{
			int min_row = Math.min(row1,row2);
			int max_row = Math.max(row1, row2);
			for(int i = min_row + 1; i < max_row ; i++)
			{
				if ( chessBoard[i][col1] != null)
					return false;
			}
			
		
		}
		
		else//for diagonal movement
		{
			
			int row_abs = Math.abs(row2 - row1);
			int col_abs = Math.abs(col2 - col1);
			if ( row_abs != col_abs )
				return false;
			
			int t_row1 = row1;
			int t_col1 = col1;

			for ( int i = 0; i < row_abs - 1 ; i++ )
			{
				if (row1 < row2 )
					t_row1++;
				else
					t_row1--;
				
				if ( col1 < col2 )
					t_col1++;
				else
					t_col1--;
				
				if (chessBoard[t_row1][t_col1] != null)
					return false;
			}
		}
		
		return true;
	}
	
	private void check_queen_movement(int row2, int col2, boolean isBlack)
	{
		check_rook_movement(row2, col2, isBlack);
		if ( !white_Check && !black_Check)
			check_bishop_movement(row2, col2, isBlack);
	}
	private boolean is_under_attack(int row2, int col2, boolean isBlack){
		int i;
		
		for ( i = row2 - 1 ; i >= 0 ; i --){
			if ((!isBlack && chessBoard[i][col2]!= null && ( chessBoard[i][col2].equals("q") || chessBoard[i][col2].equals("r")) ) ||
			     (isBlack && chessBoard[i][col2]!= null &&  ( chessBoard[i][col2].equals("Q") || chessBoard[i][col2].equals("R")) ))
				return true;
			else if ((!isBlack && chessBoard[i][col2]!=null && !chessBoard[i][col2].equals("q") && !chessBoard[i][col2].equals("r")) ||
					  (isBlack && chessBoard[i][col2]!=null && !chessBoard[i][col2].equals("Q") && !chessBoard[i][col2].equals("R"))   ){
				break;
			}
		}
		
		
		boolean stop = false;
		i = row2 -1;
		int j = col2 + 1;
		while (i >= 0 && !stop && j < 8){
			if ((!isBlack && chessBoard[i][j]!= null && ( chessBoard[i][j].equals("q") || chessBoard[i][j].equals("b")) ) ||
				 (isBlack && chessBoard[i][j]!= null && ( chessBoard[i][j].equals("Q") || chessBoard[i][j].equals("B")) ))
					return true;
			else if ((!isBlack && chessBoard[i][j]!= null && !chessBoard[i][j].equals("q") && !chessBoard[i][j].equals("b")) ||
					 (isBlack && chessBoard[i][j]!= null && !chessBoard[i][j].equals("Q") && !chessBoard[i][j].equals("B")) )
				stop = true;
			j++;
			i--;
		}

		
		for ( j = col2 + 1; j < 8 ; j++){
			if ((!isBlack && chessBoard[row2][j]!= null && ( chessBoard[row2][j].equals("q") || chessBoard[row2][j].equals("r")) ) ||
			     (isBlack && chessBoard[row2][j]!= null && ( chessBoard[row2][j].equals("Q") || chessBoard[row2][j].equals("R")) ))
				return true;
			else if ((!isBlack && chessBoard[row2][j]!= null && !chessBoard[row2][j].equals("q") && !chessBoard[row2][j].equals("r")) ||
					 ( isBlack && chessBoard[row2][j]!= null && !chessBoard[row2][j].equals("Q") && !chessBoard[row2][j].equals("R")))
				break;
		}

		
		i = row2 +1;
		j = col2 + 1;
		stop = false;
		while (i<8 && !stop && j<8){
			
			if ((!isBlack && chessBoard[i][j]!= null && ( chessBoard[i][j].equals("q") || chessBoard[i][j].equals("b")) ) ||
				 (isBlack && chessBoard[i][j]!= null && ( chessBoard[i][j].equals("Q") || chessBoard[i][j].equals("B")) ))
					return true;
			else if ((!isBlack && chessBoard[i][j]!= null && !chessBoard[i][j].equals("q") && !chessBoard[i][j].equals("b")) || 
					 ( isBlack && chessBoard[i][j]!= null && !chessBoard[i][j].equals("Q") && !chessBoard[i][j].equals("B")) )
				stop = true;
			j++;
			i++;
		}
		
		
		for ( i = row2 + 1 ; i < 8 ; i ++){
			if ((!isBlack && chessBoard[i][col2]!=null && (chessBoard[i][col2].equals("q") || chessBoard[i][col2].equals("r")) ) ||
			   (  isBlack && chessBoard[i][col2]!=null && (chessBoard[i][col2].equals("Q") || chessBoard[i][col2].equals("R")) ))
				return true;
			else if ( (!isBlack && chessBoard[i][col2]!=null && !chessBoard[i][col2].equals("q") && !chessBoard[i][col2].equals("r")) || 
					  ( isBlack && chessBoard[i][col2]!=null && !chessBoard[i][col2].equals("Q") && !chessBoard[i][col2].equals("R")) )
				break;
		}
		
		
		i = row2 + 1 ;
		j = col2 - 1;
		stop = false;
		while ( i < 8 && !stop && j >= 0){
		
			if ((!isBlack && chessBoard[i][j]!=null && ( chessBoard[i][j].equals("q") || chessBoard[i][j].equals("b")) ) ||
			     (isBlack && chessBoard[i][j]!=null && ( chessBoard[i][j].equals("Q") || chessBoard[i][j].equals("B")) ))
				return true;
			else if ((!isBlack && chessBoard[i][j]!=null && !chessBoard[i][j].equals("q") && !chessBoard[i][j].equals("b") ) ||
					  (isBlack && chessBoard[i][j]!=null && !chessBoard[i][j].equals("Q") && !chessBoard[i][j].equals("B")) ){
				stop = true;
			}
			j--;
			i++;
		}
		
		
		
		for ( j = col2 - 1; j >= 0 ; j--){
			if ((!isBlack && chessBoard[row2][j]!= null && ( chessBoard[row2][j].equals("q") || chessBoard[row2][j].equals("r")) ) ||
			     (isBlack && chessBoard[row2][j]!= null && ( chessBoard[row2][j].equals("Q") || chessBoard[row2][j].equals("R")) ))
				return true;
			else if ( (!isBlack && chessBoard[row2][j]!= null && !chessBoard[row2][j].equals("q") && !chessBoard[row2][j].equals("r")) || 
					  ( isBlack && chessBoard[row2][j]!= null && !chessBoard[row2][j].equals("Q") && !chessBoard[row2][j].equals("R"))){
				break;
			}
		}
		
		
		i = row2 -1 ;
		j = row2 - 1;
		stop = false;
		while ( i >= 0 && !stop && j >= 0){
			
			if ((!isBlack && chessBoard[i][j]!=null && ( chessBoard[i][j].equals("q") || chessBoard[i][j].equals("b")) ) ||
			     (isBlack && chessBoard[i][j]!=null && ( chessBoard[i][j].equals("Q") || chessBoard[i][j].equals("B")) ))
				return true;
			else if ( (!isBlack && chessBoard[i][j]!=null && !chessBoard[i][j].equals("q") && !chessBoard[i][j].equals("b")) ||
					  ( isBlack && chessBoard[i][j]!=null && !chessBoard[i][j].equals("Q") && !chessBoard[i][j].equals("B")) ){
				stop = true;
			}
			j--;
			i--;
		}
		
		
		if ( (row2-1) >= 0 && (col2-2) >= 0){
			String temp1 = chessBoard[row2-1][col2-2];
			if (( !isBlack && (temp1!=null && temp1.equals("n"))) || ( isBlack && (temp1!=null && temp1.equals("N"))))
				return true;
		}
		
		
		if ((row2-2) >= 0 && (col2-1) >= 0){
			String temp2 = chessBoard[row2-2][col2-1];
			if (( !isBlack && (temp2!=null && temp2.equals("n"))) || ( isBlack && (temp2!=null && temp2.equals("N"))))
				return true;
		}
		
		
		if ((row2-2) >= 0 && (col2+1) < 8){
			String temp3 = chessBoard[row2-2][col2+1];
			if (( !isBlack && (temp3!=null && temp3.equals("n"))) || ( isBlack && (temp3!=null && temp3.equals("N"))))
				return true;
		}
		
		if ((row2-1) >= 0 && (col2+2) < 8){
			String temp4 = chessBoard[row2-1][col2+2];
			if (( !isBlack && (temp4!=null && temp4.equals("n"))) || ( isBlack && (temp4!=null && temp4.equals("N"))))
				return true;
		}
		
		
		if ((row2+1) < 8 && (col2+2) < 8){
			String temp5 = chessBoard[row2+1][col2+2];
			if (( !isBlack && (temp5!=null && temp5.equals("n"))) || ( isBlack && (temp5!=null && temp5.equals("N"))))
				return true;
		}
		
		
		if ((row2+2) < 8 && (col2+1) < 8 ) {
			String temp6 = chessBoard[row2+2][col2+1];
			if (( !isBlack && (temp6!=null && temp6.equals("n"))) || ( isBlack && (temp6!=null && temp6.equals("N"))))
				return true;
		}
		
		
		if ((row2+2) < 8 && (col2-1) >= 0 ){
			String temp7 = chessBoard[row2+2][col2-1];
			if (( !isBlack && (temp7!=null && temp7.equals("n"))) || ( isBlack && (temp7!=null && temp7.equals("N"))))
				return true;
		}
		
		
		if ((row2+1) < 8 && (col2-2) >= 0 ){
			String temp8 = chessBoard[row2+1][col2-2];
			if (( !isBlack && (temp8!=null && temp8.equals("n"))) || ( isBlack && (temp8!=null && temp8.equals("N"))))
				return true;
		}
		
		
		if ((row2-1) >= 0 && (col2-1) >= 0 && (col2+1) < 8){
			String temp9 = chessBoard[row2-1][col2-1];
			String temp10 = chessBoard[row2-1][col2+1];
			
			if ( !isBlack  && ( (temp9!=null && temp9.equals("p")) || (temp10!=null && temp10.equals("p"))) )
				return true;
		}
		
		if ((row2+1) < 8 && (col2-1) >= 0 && (col2+1) < 8){
			String temp11 = chessBoard[row2+1][col2-1];
			String temp12 = chessBoard[row2+1][col2+1];
			
			if ( isBlack  && ((temp11!=null && temp11.equals("P")) || (temp12!=null && temp12.equals("P"))) )
				return true;
		}
		
		return false;
	}
	
	private boolean valid_king_movement(int row1, int col1, int row2, int col2, boolean isBlack){
		String coin = chessBoard[row1][col1];
		chessBoard[row1][col1]=null;
		if (Math.abs(row2 - row1)>1 || Math.abs(col2 - col1)>1 || is_under_attack(row2, col2, isBlack)){
			
			chessBoard[row1][col1]=coin;
			return false;
		}
		chessBoard[row1][col1]=coin;
		
		return true;
	}
	
	private boolean valid_knight_movement(int row1, int col1, int row2, int col2){
		int abs_row = Math.abs(row2 - row1);
		int abs_col = Math.abs(col2 - col1);
		
		if ((abs_row == 2 && abs_col == 1) || (abs_row == 1 && abs_col == 2))
			return true;
		
		return false;
	}
	
	private boolean is_check_knight(int i, int j, boolean isBlack){
		if ( i >= 0 && i < 8 && j >= 0 && j < 8 && chessBoard[i][j] != null && chessBoard[i][j].equals("k") && !isBlack){
			white_Check = true;
			return true;
		}else if ( i >= 0 && i < 8 && j >= 0 && j < 8 && chessBoard[i][j] != null && chessBoard[i][j].equals("K") && isBlack){
			black_Check = true;
			return true;
		}
		return false;
	}
	private void check_kniight_movement(int row2, int col2, boolean isBlack){
		int i = row2 - 2;
		int j = col2 - 1;
		if (is_check_knight(i,j,isBlack)){
			threatening_row = row2;
			threatening_col = col2;
			return;
		}
		
		j = col2 + 1;
		if (is_check_knight(i,j,isBlack)){
			threatening_row = row2;
			threatening_col = col2;
			return;
		}
		
		i = row2 - 1;
		j = col2 - 2;
		if (is_check_knight(i,j,isBlack)){
			threatening_row = row2;
			threatening_col = col2;
			return;
		}
		
		j = col2 + 2;
		if (is_check_knight(i,j,isBlack)){
			threatening_row = row2;
			threatening_col = col2;
			return;
		}
		
		i = row2 + 2;
		j = col2 - 1;
		if (is_check_knight(i,j,isBlack)){
			threatening_row = row2;
			threatening_col = col2;
			return;
		}
		
		j = col2 + 1;
		if (is_check_knight(i,j,isBlack)){
			threatening_row = row2;
			threatening_col = col2;
			return;
		}
		
		i = row2 + 1;
		j = col2 - 2;
		if (is_check_knight(i,j,isBlack)){
			threatening_row = row2;
			threatening_col = col2;
			return;
		}
		
		j = col2 + 2;
		if (is_check_knight(i,j,isBlack)){
			threatening_row = row2;
			threatening_col = col2;
			return;
		}
	}
	
	private boolean valid_pawn_movement(int row1, int col1, int row2, int col2, boolean isBlack){
		String end = chessBoard[row2][col2];
		if (!isBlack){
			
			if ( row1 == 6 && row2 == 4 && col1 == col2 && chessBoard[5][col1] == null && chessBoard[4][col1] == null)// first movement
				return true;
			
			
			else if ((row1-row2 == 1 )  && col1 == col2 && end == null)// any movement
			{
				if ( row2==0 ){
					pawn_promoted = true;
					promotion_row = row2;
					promotion_col = col2;
				}
				return true;
			
			
			}
			else if ((row1-row2 == 1 ) && Math.abs(col2 - col1)==1 && end != null && !Character.isUpperCase(end.charAt(0)))// capture
			{
				if ( row2==0 ){
					pawn_promoted = true;
					promotion_row = row2;
					promotion_col = col2;
				}
				return true;
			}
		}
		else
		{
			
			if ( row1 == 1 && row2 == 3 && col1 == col2 && chessBoard[2][col1] == null && chessBoard[3][col1] == null )// first movement
				return true;
			
			
			else if ((row2 - row1 == 1) && col1 == col2 && end == null)// any movement
			{
				if ( row2==7 )
				{
					pawn_promoted = true;
					promotion_row = row2;
					promotion_col = col2;
				}
				return true;
			
			
			}
			else if ( (row2-row1 == 1) && Math.abs(col2 - col1)==1 && end != null && Character.isUpperCase(end.charAt(0)))// capture
			{
				if ( row2==7 ){
					pawn_promoted = true;
					promotion_row = row2;
					promotion_col = col2;
				}
				return true;
			}
		}
		return false;
	}
	
	private void check_pawn_movement(int row2, int col2, boolean isBlack){
		int i = row2 - 1;
		int j = col2 - 1;
		if (!isBlack && i >= 0 && j >= 0 && chessBoard[i][j]!=null && chessBoard[i][j].equals("k")){
			white_Check = true;
			threatening_row = row2;
			threatening_col = col2;
			return;
		}
		
		j = col2 + 1;
		if (!isBlack && i >= 0 && j < 8 && chessBoard[i][j]!=null && chessBoard[i][j].equals("k")){
			white_Check = true;
			threatening_row = row2;
			threatening_col = col2;
			return;
		}
		
		i = row2 + 1;
		j = col2 - 1;
		if (isBlack && i < 8 && j >= 0 && chessBoard[i][j]!=null && chessBoard[i][j].equals("K")){
			black_Check = true;
			threatening_row = row2;
			threatening_col = col2;
			return;
		}
		
		j = col2 + 1;
		if (isBlack && i < 8 && j < 8 && chessBoard[i][j]!=null && chessBoard[i][j].equals("K")){
			black_Check = true;
			threatening_row = row2;
			threatening_col = col2;
			return;
		}
	}
	
	private boolean castling(String castling_type, boolean isBlack){
		int row1 = 0; int col1 = 0;
		int row2 = 0; int col2 = 0;
		int row3 = 0; int col3 = 0;
		boolean vacant = false;
		boolean in_position = false;
		
		if (!isBlack && castling_type.equals("short")){
			row1 = 7; col1 = 4;
			row2 = 7; col2 = 5;
			row3 = 7; col3 = 6;
			if (chessBoard[7][5] == null && chessBoard[7][6] == null)
				vacant = true;
			
			if (chessBoard[7][4] != null && chessBoard[7][4].equals("K") && chessBoard[7][7]!=null && chessBoard[7][7].equals("R"))
				in_position = true;
			
			if (is_under_attack(row1, col1, isBlack) || is_under_attack(row2, col2, isBlack) || is_under_attack(row3, col3, isBlack) || !vacant || !in_position)
				return false;
			
			chessBoard[7][4] = null; chessBoard[7][7] = null;
			chessBoard[7][6] = "K";  chessBoard[7][5] = "R";
			white_king_row = 7;
			white_king_col = 6;
			check_rook_movement(7, 5, isBlack);
			
		}else if (!isBlack && castling_type.equals("long")){
			row1 = 7; col1 = 4;
			row2 = 7; col2 = 3;
			row3 = 7; col3 = 2;
			if (chessBoard[7][1] == null && chessBoard[7][2] == null && chessBoard[7][3] == null)
				vacant = true;
			
			if (chessBoard[7][4]!=null && chessBoard[7][4].equals("K") && chessBoard[7][0]!=null && chessBoard[7][0].equals("R"))
				in_position = true;
			
			if (is_under_attack(row1, col1, isBlack) || is_under_attack(row2, col2, isBlack) || is_under_attack(row3, col3, isBlack) || !vacant || !in_position)
				return false;
			
			chessBoard[7][4] = null; chessBoard[7][0] = null;
			chessBoard[7][2] = "K";  chessBoard[7][3] = "R";
			white_king_row = 7;
			white_king_col = 2;
			check_rook_movement(7, 3, isBlack);
			
		}else if (isBlack && castling_type.equals("short")){
			row1 = 0; col1 = 4;
			row2 = 0; col2 = 5;
			row3 = 0; col3 = 6;
			if (chessBoard[0][5] == null && chessBoard[0][6] == null)
				vacant = true;
			
			if (chessBoard[0][4]!=null && chessBoard[0][4].equals("k") && chessBoard[0][7]!=null && chessBoard[0][7].equals("r"))
				in_position = true;
			
			if (is_under_attack(row1, col1, isBlack) || is_under_attack(row2, col2, isBlack) || is_under_attack(row3, col3, isBlack) || !vacant || !in_position)
				return false;
			
			chessBoard[0][4] = null; chessBoard[0][7] = null;
			chessBoard[0][6] = "k";  chessBoard[0][5] = "r";
			black_king_row = 0;
			black_king_col = 6;
			check_rook_movement(0, 5, isBlack);
			
		}else if (isBlack && castling_type.equals("long")){
			row1 = 0; col1 = 4;
			row2 = 0; col2 = 3;
			row3 = 0; col3 = 2;
			if (chessBoard[0][1] == null && chessBoard[0][2] == null && chessBoard[0][3] == null)
				vacant = true;
			
			if (chessBoard[0][4]!=null && chessBoard[0][4].equals("k") && chessBoard[0][0]!=null && chessBoard[0][0].equals("r"))
				in_position = true;
			
			if (is_under_attack(row1, col1, isBlack) || is_under_attack(row2, col2, isBlack) || is_under_attack(row3, col3, isBlack) || !vacant || !in_position)
				return false;
			
			chessBoard[0][4] = null; chessBoard[0][0] = null;
			chessBoard[0][2] = "k";  chessBoard[0][3] = "r";
			black_king_row = 0;
			black_king_col = 6;
			check_rook_movement(0, 3, isBlack);
		}
		
		
		return true;
	}
	
	private boolean check_onestep_move(int row2, int col2, boolean isBlack){
		
		if (!isBlack){
			if ( row2 >= 0 && row2 < 8 && col2 >= 0 && col2 < 8 &&
				(chessBoard[row2][col2]==null ||  Character.isUpperCase(chessBoard[row2][col2].charAt(0))) &&
				valid_king_movement(black_king_row, black_king_col, row2, col2, !isBlack))
				return true;
		}else {
			if ( row2 >= 0 && row2 < 8 && col2 >= 0 && col2 < 8 &&
					(chessBoard[row2][col2]==null ||  !Character.isUpperCase(chessBoard[row2][col2].charAt(0))) &&
					valid_king_movement(white_king_row, white_king_col, row2, col2, !isBlack))
					return true;
		}
		return false;
	}
	private boolean valid_king_move(boolean isBlack){

		if (!isBlack){
			
			if (check_onestep_move(black_king_row - 1, black_king_col, isBlack)) return true;
			if (check_onestep_move(black_king_row - 1, black_king_col + 1, isBlack)) return true;
			if (check_onestep_move(black_king_row, black_king_col + 1, isBlack)) return true;
			if (check_onestep_move(black_king_row + 1, black_king_col + 1, isBlack)) return true;
			if (check_onestep_move(black_king_row + 1, black_king_col , isBlack)) return true;
			if (check_onestep_move(black_king_row + 1, black_king_col - 1, isBlack)) return true;
			if (check_onestep_move(black_king_row, black_king_col - 1, isBlack)) return true;
			if (check_onestep_move(black_king_row - 1, black_king_col - 1, isBlack)) return true;
			
		}else{
			
			if (check_onestep_move(white_king_row - 1, white_king_col, isBlack)) return true;
			if (check_onestep_move(white_king_row - 1, white_king_col + 1, isBlack)) return true;
			if (check_onestep_move(white_king_row, white_king_col + 1, isBlack)) return true;
			if (check_onestep_move(white_king_row + 1, white_king_col + 1, isBlack)) return true;
			if (check_onestep_move(white_king_row + 1, white_king_col , isBlack)) return true;
			if (check_onestep_move(white_king_row + 1, white_king_col - 1, isBlack)) return true;
			if (check_onestep_move(white_king_row, white_king_col - 1, isBlack)) return true;
			if (check_onestep_move(white_king_row - 1, white_king_col - 1, isBlack)) return true;
		}
		
		return false;
	}
	
	private boolean check_capture(boolean isBlack){
		return is_under_attack(threatening_row, threatening_col, isBlack);
	}
	
	private boolean can_block_rook(boolean isBlack){
		
		if (!isBlack){
			if (threatening_row != black_king_row && threatening_col == black_king_col){
				int min_row = Math.min(threatening_row, black_king_row);
				int max_row = Math.max(threatening_row, black_king_row);
				for (int i = min_row + 1 ; i < max_row ; i++){
					if (can_block_in(i,black_king_col, !isBlack))
						return true;
				}
				
			}else if (threatening_col != black_king_col && threatening_row == black_king_row){
				int min_col = Math.min(threatening_col,black_king_col);
				int max_col = Math.max(threatening_col,black_king_col);
				for (int j = min_col + 1 ; j < max_col ; j++){
					if (can_block_in(black_king_row,j, !isBlack))
						return true;
				}
			}
		}else{
			if (threatening_row != white_king_row && threatening_col == white_king_col){
				int min_row = Math.min(threatening_row, white_king_row);
				int max_row = Math.max(threatening_row, white_king_row);
				for (int i = min_row + 1 ; i < max_row ; i++){
					if (can_block_in(i,white_king_col, !isBlack))
						return true;
				}
				
			}else if (threatening_col != white_king_col && threatening_row == white_king_row){
				int min_col = Math.min(threatening_col,white_king_col);
				int max_col = Math.max(threatening_col,white_king_col);
				for (int j = min_col + 1 ; j < max_col ; j++){
					if (can_block_in(white_king_row,j, !isBlack))
						return true;
				}
			}
		}
		return false;
	}
	
	private boolean can_block_in(int row2, int col2, boolean isBlack){
		if (!isBlack){
	
			if (( (row2 +1) < 8 && chessBoard[row2 +1][col2]!=null && chessBoard[row2 +1][col2].equals("P")) ||
				( (row2 +2) == 6 && chessBoard[row2+2][col2]!=null && chessBoard[row2+2][col2].equals("P"))	)
				return true;
		}else{
	
			if (( (row2 - 1) > 0 && chessBoard[row2 -1][col2]!=null && chessBoard[row2 -1][col2].equals("p")) ||
				( (row2 -2) == 1 && chessBoard[row2-2][col2]!=null && chessBoard[row2-2][col2].equals("p"))	)
					return true;
		}
		
		
		int i;
	
		for ( i = row2 - 1 ; i >= 0 ; i --){
			if ((!isBlack && chessBoard[i][col2]!= null && ( chessBoard[i][col2].equals("Q") || chessBoard[i][col2].equals("R")) ) ||
			     (isBlack && chessBoard[i][col2]!= null &&  ( chessBoard[i][col2].equals("q") || chessBoard[i][col2].equals("r")) ))
				return true;
			else if ((!isBlack && chessBoard[i][col2]!=null && !chessBoard[i][col2].equals("Q") && !chessBoard[i][col2].equals("R")) ||
					  (isBlack && chessBoard[i][col2]!=null && !chessBoard[i][col2].equals("q") && !chessBoard[i][col2].equals("r"))   ){
				break;
			}
		}
		
	
		boolean stop = false;
		i = row2 -1;
		int j = col2 + 1;
		while (i >= 0 && !stop && j < 8){
			if ((!isBlack && chessBoard[i][j]!= null && ( chessBoard[i][j].equals("Q") || chessBoard[i][j].equals("B")) ) ||
				 (isBlack && chessBoard[i][j]!= null && ( chessBoard[i][j].equals("q") || chessBoard[i][j].equals("b")) ))
					return true;
			else if ((!isBlack && chessBoard[i][j]!= null && !chessBoard[i][j].equals("Q") && !chessBoard[i][j].equals("B")) ||
					 (isBlack && chessBoard[i][j]!= null && !chessBoard[i][j].equals("q") && !chessBoard[i][j].equals("b")) )
				stop = true;
			j++;
			i--;
		}

	
		for ( j = col2 + 1; j < 8 ; j++){
			if ((!isBlack && chessBoard[row2][j]!= null && ( chessBoard[row2][j].equals("Q") || chessBoard[row2][j].equals("R")) ) ||
			     (isBlack && chessBoard[row2][j]!= null && ( chessBoard[row2][j].equals("q") || chessBoard[row2][j].equals("r")) ))
				return true;
			else if ((!isBlack && chessBoard[row2][j]!= null && !chessBoard[row2][j].equals("Q") && !chessBoard[row2][j].equals("R")) ||
					 ( isBlack && chessBoard[row2][j]!= null && !chessBoard[row2][j].equals("q") && !chessBoard[row2][j].equals("r")))
				break;
		}

	
		i = row2 +1;
		j = col2 + 1;
		stop = false;
		while (i<8 && !stop && j<8){
			
			if ((!isBlack && chessBoard[i][j]!= null && ( chessBoard[i][j].equals("Q") || chessBoard[i][j].equals("B")) ) ||
				 (isBlack && chessBoard[i][j]!= null && ( chessBoard[i][j].equals("q") || chessBoard[i][j].equals("b")) ))
					return true;
			else if ((!isBlack && chessBoard[i][j]!= null && !chessBoard[i][j].equals("Q") && !chessBoard[i][j].equals("B")) || 
					 ( isBlack && chessBoard[i][j]!= null && !chessBoard[i][j].equals("q") && !chessBoard[i][j].equals("b")) )
				stop = true;
			j++;
			i++;
		}
		
	
		for ( i = row2 + 1 ; i < 8 ; i ++){
			if ((!isBlack && chessBoard[i][col2]!=null && (chessBoard[i][col2].equals("Q") || chessBoard[i][col2].equals("R")) ) ||
			   (  isBlack && chessBoard[i][col2]!=null && (chessBoard[i][col2].equals("q") || chessBoard[i][col2].equals("r")) ))
				return true;
			else if ( (!isBlack && chessBoard[i][col2]!=null && !chessBoard[i][col2].equals("Q") && !chessBoard[i][col2].equals("R")) || 
					  ( isBlack && chessBoard[i][col2]!=null && !chessBoard[i][col2].equals("q") && !chessBoard[i][col2].equals("r")) )
				break;
		}
		
	
		i = row2 + 1 ;
		j = col2 - 1;
		stop = false;
		while ( i < 8 && !stop && j >= 0){
		
			if ((!isBlack && chessBoard[i][j]!=null && ( chessBoard[i][j].equals("Q") || chessBoard[i][j].equals("B")) ) ||
			     (isBlack && chessBoard[i][j]!=null && ( chessBoard[i][j].equals("q") || chessBoard[i][j].equals("b")) ))
				return true;
			else if ((!isBlack && chessBoard[i][j]!=null && !chessBoard[i][j].equals("Q") && !chessBoard[i][j].equals("B") ) ||
					  (isBlack && chessBoard[i][j]!=null && !chessBoard[i][j].equals("q") && !chessBoard[i][j].equals("b")) ){
				stop = true;
			}
			j--;
			i++;
		}
		
		
	
		for ( j = col2 - 1; j >= 0 ; j--){
			if ((!isBlack && chessBoard[row2][j]!= null && ( chessBoard[row2][j].equals("Q") || chessBoard[row2][j].equals("R")) ) ||
			     (isBlack && chessBoard[row2][j]!= null && ( chessBoard[row2][j].equals("q") || chessBoard[row2][j].equals("r")) ))
				return true;
			else if ( (!isBlack && chessBoard[row2][j]!= null && !chessBoard[row2][j].equals("Q") && !chessBoard[row2][j].equals("R")) || 
					  ( isBlack && chessBoard[row2][j]!= null && !chessBoard[row2][j].equals("q") && !chessBoard[row2][j].equals("r"))){
				break;
			}
		}
		
	
		i = row2 -1 ;
		j = col2 - 1;
		stop = false;
		while ( i >= 0 && !stop && j >= 0){
			
			if ((!isBlack && chessBoard[i][j]!=null && ( chessBoard[i][j].equals("Q") || chessBoard[i][j].equals("B")) ) ||
			     (isBlack && chessBoard[i][j]!=null && ( chessBoard[i][j].equals("q") || chessBoard[i][j].equals("b")) ))
				return true;
			else if ( (!isBlack && chessBoard[i][j]!=null && !chessBoard[i][j].equals("Q") && !chessBoard[i][j].equals("B")) ||
					  ( isBlack && chessBoard[i][j]!=null && !chessBoard[i][j].equals("q") && !chessBoard[i][j].equals("b")) ){
				stop = true;
			}
			j--;
			i--;
		}
		
	
		if ( (row2-1) >= 0 && (col2-2) >= 0){
			String temp1 = chessBoard[row2-1][col2-2];
			if (( !isBlack && (temp1!=null && temp1.equals("N"))) || ( isBlack && (temp1!=null && temp1.equals("n"))))
				return true;
		}
		
		if ((row2-2) >= 0 && (col2-1) >= 0){
			String temp2 = chessBoard[row2-2][col2-1];
			if (( !isBlack && (temp2!=null && temp2.equals("N"))) || ( isBlack && (temp2!=null && temp2.equals("n"))))
				return true;
		}

		if ((row2-2) >= 0 && (col2+1) < 8){
			String temp3 = chessBoard[row2-2][col2+1];
			if (( !isBlack && (temp3!=null && temp3.equals("N"))) || ( isBlack && (temp3!=null && temp3.equals("n"))))
				return true;
		}
		
		if ((row2-1) >= 0 && (col2+2) < 8){
			String temp4 = chessBoard[row2-1][col2+2];
			if (( !isBlack && (temp4!=null && temp4.equals("N"))) || ( isBlack && (temp4!=null && temp4.equals("n"))))
				return true;
		}
		
		
		if ((row2+1) < 8 && (col2+2) < 8){
			String temp5 = chessBoard[row2+1][col2+2];
			if (( !isBlack && (temp5!=null && temp5.equals("N"))) || ( isBlack && (temp5!=null && temp5.equals("n"))))
				return true;
		}
		
		
		if ((row2+2) < 8 && (col2+1) < 8 ) {
			String temp6 = chessBoard[row2+2][col2+1];
			if (( !isBlack && (temp6!=null && temp6.equals("N"))) || ( isBlack && (temp6!=null && temp6.equals("n"))))
				return true;
		}
		
		
		if ((row2+2) < 8 && (col2-1) >= 0 ){
			String temp7 = chessBoard[row2+2][col2-1];
			if (( !isBlack && (temp7!=null && temp7.equals("N"))) || ( isBlack && (temp7!=null && temp7.equals("n"))))
				return true;
		}
		
		
		if ((row2+1) < 8 && (col2-2) >= 0 ){
			String temp8 = chessBoard[row2+1][col2-2];
			if (( !isBlack && (temp8!=null && temp8.equals("N"))) || ( isBlack && (temp8!=null && temp8.equals("n"))))
				return true;
		}
		
		return false;
		
	}
	
	
	private boolean can_block_bishop(boolean isBlack){
		if (!isBlack){
			if (threatening_row < black_king_row && threatening_col > black_king_col){
				int i = black_king_row - 1;
				int j = black_king_col + 1 ;
				while ( i > threatening_row && j < threatening_col ){
					if (can_block_in(i,j,!isBlack)){
						return true;
					}
					i--;
					j++;
				}
			}else if (threatening_row > black_king_row && threatening_col > black_king_col){
				int i = black_king_row + 1;
				int j = black_king_col + 1;
				while ( i < threatening_row && j < threatening_col){
					if (can_block_in(i,j,!isBlack)){
						return true;
					}
					i++;
					j++;
				}
			}else if (threatening_row > black_king_row && threatening_col < black_king_col){
				int i = black_king_row + 1;
				int j = black_king_col - 1;
				while ( i < threatening_row && j > threatening_col){
					if (can_block_in(i,j,!isBlack)){
						return true;
					}
					i++;
					j--;
				}
			}else if (threatening_row < black_king_row && threatening_col < black_king_col){
				int i = black_king_row - 1;
				int j = black_king_col - 1;
				while(i > threatening_row && j > threatening_col){
					if (can_block_in(i,j,!isBlack)){
						return true;
					}
					i--;
					j--;
				}
			}
		}else{
			if (threatening_row < white_king_row && threatening_col > white_king_col){
				int i = white_king_row - 1;
				int j = white_king_col + 1 ;
				while ( i > threatening_row && j < threatening_col ){
					if (can_block_in(i,j,!isBlack)){
						return true;
					}
					i--;
					j++;
				}
			}else if (threatening_row > white_king_row && threatening_col > white_king_col){
				int i = white_king_row + 1;
				int j = white_king_col + 1;
				while ( i < threatening_row && j < threatening_col){
					if (can_block_in(i,j,!isBlack)){
						return true;
					}
					i++;
					j++;
				}
			}else if (threatening_row > white_king_row && threatening_col < white_king_col){
				int i = white_king_row + 1;
				int j = white_king_col - 1;
				while ( i < threatening_row && j > threatening_col){
					if (can_block_in(i,j,!isBlack)){
						return true;
					}
					i++;
					j--;
				}
			}else if (threatening_row < white_king_row && threatening_col < white_king_col){
				int i = white_king_row - 1;
				int j = white_king_col - 1;
				while(i > threatening_row && j > threatening_col){
					if (can_block_in(i,j,!isBlack)){
						return true;
					}
					i--;
					j--;
				}
			}
		}
		return false;
		
	}
	
	private boolean can_block_queen(boolean isBlack){
		if (can_block_rook(isBlack))
			return true;
		else{
			return can_block_bishop(isBlack);
		}
		
		
	}
	
	private boolean canBlock(boolean isBlack){
		String threat_coin = chessBoard[threatening_row][threatening_col];
		if (threat_coin != null){
			if (threat_coin.equals("n") || threat_coin.equals("p")){
				return false;
				
			}
				
			
			if (threat_coin.equals("r")){
				return can_block_rook(isBlack);
			}
		
			if (threat_coin.equals("b")){
				can_block_bishop(isBlack);
			}
			
			if (threat_coin.equals("q")){
				return can_block_queen(isBlack);
			}
			
		}
		return false;
	}
	private void is_check_mate(boolean isBlack){
		boolean a = valid_king_move(isBlack);
		boolean b = check_capture(isBlack);
		boolean c = canBlock(isBlack);
		
		if ( !a && !b && !c){
			if (isBlack)
			{
				black_check_mate = 0; 
				white_check_mate = 1;
			}
			
			else
			{					
				white_check_mate = 0; 
				black_check_mate = 1;
			}
		}
		
		
					
	}

	public boolean is_valid_move(String at, String to, boolean isBlack)
	{
		if (!isBlack)
			black_Check = false;
		else
			white_Check = false;
			
		String[] parts1 = at.split(" ");
		String[] parts2 = to.split(" ");
		
		// working on Castling
		if (parts1.length == 2 && parts1[0].equals("castling") && 
		   (parts1[1].equals("short") || parts1[1].equals("long"))){
			
			if ((!isBlack && parts1[1].equals("short") && !white_castling_right) || 
				(!isBlack && parts1[1].equals("long") && !white_castling_left) ||
				( isBlack && parts1[1].equals("short") && !white_castling_right) ||
				( isBlack && parts1[1].equals("long") && !white_castling_left)){
				return false;
				
			}else{
				return castling(parts1[1], isBlack);
			}
		}
				
		if (parts1.length != 2 || parts2.length != 2)
			return false;
					
		int row1 = Integer.parseInt(parts1[0]);
		int col1 = Integer.parseInt(parts1[1]);
		int row2 = Integer.parseInt(parts2[0]);
		int col2 = Integer.parseInt(parts2[1]);
		
		if ( row1 < 1 || row1 > 8 || col1 < 1 || col1 > 8 || row2 < 1 || row2 > 8 || col2 < 1 || col2 > 8)
			return false;
		
		row1 --; col1--; row2--; col2--;
		
		if (isBlack){
			row1 = 8 - row1 - 1;
			col1 = 8 - col1 - 1;
			row2 = 8 - row2 - 1;
			col2 = 8 - col2 - 1;
		}
		
		String piece = chessBoard[row1][col1];
		String end = chessBoard[row2][col2];
		
		if (( row1==row2 && col1==col2) || (piece == null))
			return false;
		
		if (!isBlack && !Character.isUpperCase(piece.charAt(0)))
			return false;
		
		if (isBlack && Character.isUpperCase(piece.charAt(0)))
			return false;
			
		if (!isBlack && end != null && Character.isUpperCase(end.charAt(0)))
			return false;
		
		if (isBlack && end != null && !Character.isUpperCase(end.charAt(0)))
			return false;
				
		
		if (piece.equals("r")){
			if (!valid_rook_movement(row1, col1, row2, col2))
				return false;
			else
				check_rook_movement(row2, col2, isBlack);
		}
		
		
		if (piece.equals("b")){
			if (!valid_bishop_movement(row1, col1, row2, col2))
				return false;
			else 
				check_bishop_movement(row2, col2, isBlack);
		}
		
		
		if (piece.equals("q")){
			if (!valid_queen_movement(row1, col1, row2, col2))
				return false;
			else
				check_queen_movement(row2, col2, isBlack);
		}
		
		
		if (piece.equals("k")){
			if (!valid_king_movement(row1, col1, row2, col2, isBlack))
				return false;
			else{
				if (!isBlack){
					white_king_row = row2;
					white_king_col = col2;
				
				}else{
					black_king_row = row2;
					black_king_col = col2;
				}
			}
		}
		
		if (piece.equals("n")){
			if (!valid_knight_movement(row1, col1, row2, col2))
				return false;
			else 
				check_kniight_movement(row2, col2, isBlack);
		}
		
		
		if (piece.equals("p")){
			if (!valid_pawn_movement(row1, col1, row2, col2, isBlack))
				return false;
			else
				check_pawn_movement(row2, col2, isBlack);
		}
		
	
		if (!isBlack){
			if (white_castling_left && row1==7 && col1==0 ){
				white_castling_left = false;
				
			}else if (white_castling_right && row1==7 && col1==7){
				white_castling_right = false;
				
			}else if((white_castling_left || white_castling_right) && row1==7 && col1==4){
				white_castling_left = false;
				white_castling_right = false;
			}
		}else{
			if (black_castling_left && row1==0 && col1==0){
				black_castling_left = false;
			
			}else if (black_castling_right && row1==0 && col1==7){
				black_castling_right = false;
				
			}else if ((black_castling_left || black_castling_right) && row1==0 && col1==4){
				black_castling_left = false;
				black_castling_right = false;
			}
		}
		
		
		/* move piece */
		chessBoard[row1][col1] = null;
		chessBoard[row2][col2] = piece;
		
		if (!isBlack){
			if (is_under_attack(white_king_row, white_king_col, isBlack)){
				System.out.println("white king is under attack");
				chessBoard[row1][col1] = piece;
				chessBoard[row2][col2] = end;
				white_Check = false;
				return false;
				
			}	
		}else{
			if (is_under_attack(black_king_row, black_king_col, isBlack)){
				System.out.println("Black king is under attack");
				chessBoard[row1][col1] = piece;
				chessBoard[row2][col2] = end;
				black_Check = false;
				return false;
			}
		}
		
		if (white_Check){
			System.out.println("'Check' from White force");
			is_check_mate(isBlack);
				
		}else if (black_Check){
			System.out.println("Check from Black force");
			is_check_mate(isBlack);
		}
		return true;
		
	}

}


