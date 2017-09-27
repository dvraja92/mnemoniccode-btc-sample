package com.decipher.btcwallet;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BITToWallet {

    private static final Logger logger = LoggerFactory.getLogger(BITToWallet.class);

        public static void main(String[] args) throws Exception {
            NetworkParameters params = TestNet3Params.get();

            String seedCode = "yard impulse luxury drive today throw farm pepper survey wreck glass federal";
            String passphrase = "";
            Long creationtime = 1409478661L;

            DeterministicSeed seed = new DeterministicSeed(seedCode, null, passphrase, creationtime);

            Wallet wallet = Wallet.fromSeed(params, seed);

            logger.info(wallet.toString());
            wallet.clearTransactions(0);
            File chainFile = new File("restore-from-seed.spvchain");
            if (chainFile.exists()) {
                chainFile.delete();
            }

            SPVBlockStore chainStore = new SPVBlockStore(params, chainFile);
            BlockChain chain = new BlockChain(params, chainStore);
            PeerGroup peers = new PeerGroup(params, chain);
            peers.addPeerDiscovery(new DnsDiscovery(params));

            chain.addWallet(wallet);
            peers.addWallet(wallet);

            DownloadProgressTracker bListener = new DownloadProgressTracker() {
                @Override
                public void doneDownload() {
                    logger.info("blockchain downloaded");
                }
            };

            peers.start();
            peers.startBlockChainDownload(bListener);

            bListener.await();

            logger.info(wallet.toString());

            peers.stop();
        }

}
