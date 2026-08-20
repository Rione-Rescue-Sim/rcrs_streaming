import rescuecore2.log.RCRSLogFactory;
import rescuecore2.log.LogReader;
import rescuecore2.log.CommandsRecord;
import rescuecore2.messages.Command;
import rescuecore2.registry.Registry;
import rescuecore2.standard.entities.*;
import rescuecore2.standard.messages.StandardMessageFactory;

import rescuecore2.standard.messages.AKClear;
import rescuecore2.standard.messages.AKClearArea;
import rescuecore2.standard.messages.AKLoad;
import rescuecore2.standard.messages.AKUnload;
import rescuecore2.standard.messages.AKRescue;
import rescuecore2.standard.messages.AKMove;
import rescuecore2.standard.messages.AKRest;
import rescuecore2.standard.messages.AKSay;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.standard.messages.AKTell;
import rescuecore2.standard.messages.AKSubscribe;
import rescuecore2.standard.messages.AKExtinguish;

import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.worldmodel.WorldModel;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * RoboCup Rescueのバイナリログ(.log)を読み込み、
 * LLM実況に必要な「重要な状態変化」のみをJSONL形式で出力する軽量化版ツール
 */
public class JsonDataExtractor {

    // 状態の差分を検知するために、前サイクルの状態を保持するマップ
    private static Map<EntityID, Integer> previousFieryness = new HashMap<>();
    private static Map<EntityID, Integer> previousHP = new HashMap<>();
    private static Map<EntityID, Integer> previousBuriedness = new HashMap<>();
    // 位置(移動)の差分を検知するためのマップを追加
    private static Map<EntityID, EntityID> previousPosition = new HashMap<>();
    private static Map<EntityID, String> commandMap = new HashMap<>();
    private static Map<EntityID, EntityID> commandTargetMap = new HashMap<>();
    private static Map<EntityID, String> speakMap = new HashMap<>();
    private static Map<EntityID, String> tellMap = new HashMap<>();

    // ★追加: バイト配列を16進数文字列に変換するヘルパーメソッド
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ★追加: バイト配列をテキスト（表示可能なASCII文字のみ）に変換するヘルパーメソッド
    private static String bytesToCleanString(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            if (b >= 32 && b <= 126) {
                if (b == '"' || b == '\\') {
                    sb.append('\\'); // JSON用にダブルクォートとバックスラッシュをエスケープ
                }
                sb.append((char)b);
            } else {
                sb.append('.'); // 読めない文字はドットに置き換え
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java JsonDataExtractor <input.log> <output.jsonl>");
            return;
        }

        String logFilePath = args[0];
        String outputFilePath = args[1];

        // どこに保存されるか、絶対パスを取得して表示する
        File outFile = new File(outputFilePath);
        System.out.println("--- JsonDataExtractor 処理開始 ---");
        System.out.println("入力ログ: " + new File(logFilePath).getAbsolutePath());
        System.out.println("出力先　: " + outFile.getAbsolutePath());
        System.out.println("データの抽出を行っています...");

        // 1. RoboCup Rescueの標準エンティティやプロパティをシステムに登録する
        Registry.SYSTEM_REGISTRY.registerFactory(StandardEntityFactory.INSTANCE);
        Registry.SYSTEM_REGISTRY.registerFactory(StandardMessageFactory.INSTANCE);
        Registry.SYSTEM_REGISTRY.registerFactory(StandardPropertyFactory.INSTANCE);

        // JSONL(JSON Lines)形式で出力する
        try (PrintWriter writer = new PrintWriter(new FileWriter(outFile))) {
            // ログリーダーの初期化 (RCRSLogFactoryを使用して安全に読み込む)
            LogReader reader = RCRSLogFactory.getLogReader(logFilePath, Registry.SYSTEM_REGISTRY);
            
            // 2. ログの最大サイクル数を取得し、ループで回す
            int maxTime = reader.getMaxTimestep();
            System.out.println("最大サイクル数: " + maxTime);
            for (int time = 0; time <= maxTime; ++time) {
                
                // そのサイクルの「世界全体の状態(WorldModel)」を取得
                WorldModel<? extends Entity> model = reader.getWorldModel(time);
                if (model == null) continue;

                StringBuilder cycleEvents = new StringBuilder();
                boolean hasEvent = false;

                // マップのクリア
                commandMap.clear();
                commandTargetMap.clear();
                speakMap.clear();
                tellMap.clear();

                // 4. エージェントのコマンド（アクション）を確認
                CommandsRecord commandsRecord = reader.getCommands(time);
                if (commandsRecord != null) {
                    for (Command command : commandsRecord.getCommands()) {
                        if (command instanceof AKSpeak) {
                            AKSpeak speak = (AKSpeak) command;
                            speakMap.put(speak.getAgentID(), command.getClass().getSimpleName());
                            
                            // ★追加: AKSpeak(声・無線)の通信内容を抽出
                            byte[] content = speak.getContent();
                            if (hasEvent) cycleEvents.append(", ");
                            cycleEvents.append(String.format("{\"type\": \"communication\", \"agent_id\": %d, \"action\": \"AKSpeak\", \"channel\": %d, \"size\": %d, \"text_preview\": \"%s\", \"hex\": \"%s\"}", 
                                speak.getAgentID().getValue(), speak.getChannel(), content.length, bytesToCleanString(content), bytesToHex(content)));
                            hasEvent = true;
                        }
                        else if (command instanceof AKTell) {
                            AKTell tell = (AKTell) command;
                            tellMap.put(tell.getAgentID(), command.getClass().getSimpleName());
                            
                            // ★追加: AKTell(無線)の通信内容を抽出
                            byte[] content = tell.getContent();
                            if (hasEvent) cycleEvents.append(", ");
                            // AKTellは基本的に無線用コマンドです
                            cycleEvents.append(String.format("{\"type\": \"communication\", \"agent_id\": %d, \"action\": \"AKTell\", \"size\": %d, \"text_preview\": \"%s\", \"hex\": \"%s\"}", 
                                tell.getAgentID().getValue(), content.length, bytesToCleanString(content), bytesToHex(content)));
                            hasEvent = true;
                        }
                        else if (command instanceof AKSay) {
                            AKSay say = (AKSay) command;
                            speakMap.put(say.getAgentID(), command.getClass().getSimpleName());
                            
                            // ★追加: AKSay(旧式の肉声)の通信内容を抽出
                            byte[] content = say.getContent();
                            if (hasEvent) cycleEvents.append(", ");
                            cycleEvents.append(String.format("{\"type\": \"communication\", \"agent_id\": %d, \"action\": \"AKSay\", \"channel\": 0, \"size\": %d, \"text_preview\": \"%s\", \"hex\": \"%s\"}", 
                                say.getAgentID().getValue(), content.length, bytesToCleanString(content), bytesToHex(content)));
                            hasEvent = true;
                        }
                        else {
                            commandMap.put(command.getAgentID(), command.getClass().getSimpleName());

                            // ★修正: キャストミスを直し、ターゲットを持たないコマンド(AKClearArea, AKUnload等)を除外
                            if (command instanceof AKClear) {
                                commandTargetMap.put(command.getAgentID(), ((AKClear)command).getTarget());
                            }
                            else if (command instanceof AKRescue) {
                                commandTargetMap.put(command.getAgentID(), ((AKRescue)command).getTarget());
                            }
                            else if (command instanceof AKLoad) {
                                commandTargetMap.put(command.getAgentID(), ((AKLoad)command).getTarget());
                            }
                            else if (command instanceof AKExtinguish) {
                                commandTargetMap.put(command.getAgentID(), ((AKExtinguish)command).getTarget());
                            }
                        }
                    }
                }

                // 3. 世界に存在するすべてのエンティティを確認
                for (Entity entity : model) {

                    // Civilianとその他のHumanの処理を共通化してスッキリさせました
                    if (entity instanceof Human) {
                        Human h  = (Human)entity;
                        int currentBuriedness = h.getBuriedness();
                        int prevBuriedness = previousBuriedness.getOrDefault(h.getID(), -1);
                        int currentPosition = h.isPositionDefined() ? h.getPosition().getValue() : -1;
                        int x = h.isXDefined() ? h.getX() : -1;
                        int y = h.isYDefined() ? h.getY() : -1;

                        boolean isScreem = false;
                        String action = "NULL";
                        int actionTarget = 0; // ターゲットが0なら対象なしとする

                        if (speakMap.containsKey(h.getID())){
                            isScreem = true;
                        }
                        if (commandMap.containsKey(h.getID())){
                            action = commandMap.get(h.getID());
                        }
                        if (commandTargetMap.containsKey(h.getID())) {
                            actionTarget = commandTargetMap.get(h.getID()).getValue();
                        }

                        if (hasEvent) cycleEvents.append(", ");

                        String className = h.getClass().getSimpleName();
                        cycleEvents.append(String.format("{\"type\": \"%s\", \"id\": %d, \"hp\": %d, \"burriedness\": %d, \"position\": %d, \"x\": %d, \"y\": %d, \"Screem\":%b, \"Action\": \"%s\", \"Target\":%d}", 
                        className, h.getID().getValue(), h.getHP(), h.getBuriedness(), currentPosition, x, y, isScreem, action, actionTarget));
                        hasEvent = true;
                    }
                    
                    // --- 建物の火災(fieryness)変化を抽出 ---
                    else if (entity instanceof Building) {
                        Building b = (Building) entity;
                        if (b.isFierynessDefined()) {
                            int currentFieryness = b.getFieryness();
                            int prevFieryness = previousFieryness.getOrDefault(b.getID(), 0);
                            
                            if (currentFieryness != prevFieryness) {
                                if (hasEvent) cycleEvents.append(", ");
                                cycleEvents.append(String.format("{\"type\": \"building_fire_changed\", \"id\": %d, \"from\": %d, \"to\": %d}", 
                                    b.getID().getValue(), prevFieryness, currentFieryness));
                                previousFieryness.put(b.getID(), currentFieryness);
                                hasEvent = true;
                            }
                        }
                    }
                }
                
                // そのサイクルで何らかのイベント・情報があれば出力
                if (hasEvent) {
                    writer.println(String.format("{\"cycle\": %d, \"events\": [%s]}", time, cycleEvents.toString()));
                }
            }
            
            System.out.println("--- 処理完了 ---");
            System.out.println("JSONLファイルの出力が完了しました: " + outFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("エラーが発生しました:");
            e.printStackTrace();
        }
    }
}