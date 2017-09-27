package com.decipher.btcwallet;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class BackupToMnemonicSeed {

    private static final Logger logger = LoggerFactory.getLogger(BackupToMnemonicSeed.class);

    public static void main(String[] args) {

        NetworkParameters params = TestNet3Params.get();
        Wallet wallet = new Wallet(params);

        DeterministicSeed seed = wallet.getKeyChainSeed();
        logger.info("seed: " + seed.toString());

        logger.info("creation time: " + seed.getCreationTimeSeconds());
        logger.info("mnemonicCode: " + Utils.join(seed.getMnemonicCode()));

        try {
            wallet.saveToFile(new File("restore-from-seed.spvchain"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}