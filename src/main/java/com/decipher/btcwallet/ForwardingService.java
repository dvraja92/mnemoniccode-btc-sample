package com.decipher.btcwallet;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.listeners.PreMessageReceivedEventListener;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.core.Coin.CENT;
import static org.bitcoinj.core.Coin.SATOSHI;

/**
 * It sits on the network and when it receives coins, simply
 * sends them onwards to an address given on the command line.
 */
public class ForwardingService {

    private static final Logger logger = LoggerFactory.getLogger(ForwardingService.class);
    private static Address forwardingAddress;
    private static WalletAppKit kit;

    public static void main(String[] args) {
        BriefLogFormatter.init();
        if (args.length < 1) {
            logger.error("Usage: address-to-send-back-to [regtest|testnet]");
            return;
        }

        final NetworkParameters params;
        String filePrefix;
        if (args.length > 1 && args[1].equals("testnet")) {
            params = TestNet3Params.get();
            filePrefix = "forwarding-service-testnet";
        } else if (args.length > 1 && args[1].equals("regtest")) {
            params = RegTestParams.get();
            filePrefix = "forwarding-service-regtest";
        } else {
            params = MainNetParams.get();
            filePrefix = "forwarding-service";
        }
        forwardingAddress = Address.fromBase58(params, args[0]);

        kit = new WalletAppKit(params, new File("."), filePrefix);

        if (params == RegTestParams.get()) {
            kit.connectToLocalHost();
        }

        kit.startAsync();
        kit.awaitRunning();
        Wallet wallet = kit.wallet();
        DeterministicSeed keyChainSeed = wallet.getKeyChainSeed();

        logger.info("seed: " + keyChainSeed.toString());
        logger.info("creation time: " + keyChainSeed.getCreationTimeSeconds());
        logger.info("mnemonicCode: " + Utils.join(keyChainSeed.getMnemonicCode()));

        kit.wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet w, final Transaction tx, Coin prevBalance, Coin newBalance) {

                Coin value = tx.getValueSentToMe(w);
                logger.info("Received tx for " + value.toFriendlyString() + ": " + tx);
                logger.info("Transaction will be forwarded after it confirms.");

                Futures.addCallback(tx.getConfidence().getDepthFuture(1), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        forwardCoins(tx);
//                        try {
//                            sendCoins(params, kit);
//                        } catch (InsufficientMoneyException e) {
//                            logger.error(e.getMessage(), e);
//                        }
                    }
                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                });
            }
        });

        Address sendToAddress = kit.wallet().currentReceiveKey().toAddress(params);
        logger.info("Send coins to: " + sendToAddress);
        logger.info("Waiting for coins to arrive. Press Ctrl-C to quit.");

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();  // set interrupt flag
        }
    }

    /**
     * Forward transaction request for confirmation from the miners
     * @param tx {@link Transaction}
     */
    private static void forwardCoins(Transaction tx) {
        try {
            Coin value = tx.getValueSentToMe(kit.wallet());

            final Coin amountToSend = value.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
            final Wallet.SendResult sendResult = kit.wallet().sendCoins(kit.peerGroup(), forwardingAddress, amountToSend);
            checkNotNull(sendResult);

            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    logger.info("Sent coins onwards! Transaction hash is " + sendResult.tx.getHashAsString());
                }
            }, MoreExecutors.directExecutor());
        } catch (KeyCrypterException e) {
            logger.error("Error occured while transfering wallet "+e);
        } catch (InsufficientMoneyException e) {
            logger.error("Error occured while transfering due to insufficient money "+e);
        }
    }

    /**
     * Send coins to Bitcoin Address
     * @param params {@link NetworkParameters}
     * @param kit {@link WalletAppKit}
     * @throws InsufficientMoneyException
     */
    private static void sendCoins(NetworkParameters params, WalletAppKit kit) throws InsufficientMoneyException {
        Transaction tx1 = kit.wallet().createSend(Address.fromBase58(params, "moK3r6HiYUsLXyff46sAt45P6Y5ocdNeH6"), CENT);
        Transaction tx2 = kit.wallet().createSend(Address.fromBase58(params, "moK3r6HiYUsLXyff46sAt45P6Y5ocdNeH6"), CENT.add(SATOSHI.multiply(10)));
        final Peer peer = kit.peerGroup().getConnectedPeers().get(0);
        kit.peerGroup().addPreMessageReceivedEventListener(Threading.SAME_THREAD,
                new PreMessageReceivedEventListener() {
                    @Override
                    public Message onPreMessageReceived(Peer peer, Message m) {
                        logger.info("Got a message!" + m.getClass().getSimpleName() + ": " + m);
                        return m;
                    }
                }
        );

        peer.sendMessage(tx1);
        peer.sendMessage(tx2);
    }



}
