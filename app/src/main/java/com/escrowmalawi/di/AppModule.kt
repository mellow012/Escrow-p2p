package com.escrowmalawi.di

import com.escrowmalawi.blockchain.EthereumService
import com.escrowmalawi.data.local.WalletManager
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides @Singleton
    fun provideWalletManager(@ApplicationContext context: Context): WalletManager = WalletManager(context)

    @Provides @Singleton
    fun provideNotificationRepository(firestore: FirebaseFirestore): com.escrowmalawi.data.repository.NotificationRepository =
        com.escrowmalawi.data.repository.NotificationRepository(firestore)

    // ── Cloudinary (Free image hosting — no bucket setup needed) ──────────
    // Steps:
    //  1. Sign up free at https://cloudinary.com
    //  2. Copy your "Cloud name" from the dashboard home page
    //  3. Go to Settings → Upload → "Add upload preset"
    //     Set Mode = "Unsigned", give it a name e.g. "escrow_malawi", save.
    //  4. Replace the placeholder strings below with your actual values.

    @Provides @Named("cloudinaryCloudName")
    fun provideCloudinaryCloudName(): String = "du07oykqy"   // ← replace

    @Provides @Named("cloudinaryUploadPreset")
    fun provideCloudinaryUploadPreset(): String = "escrow-malawi-app"  // ← replace

    // ── Ethereum / Web3j ──────────────────────────────────────────────────
    @Provides @Named("rpcUrl")
    fun provideRpcUrl(): String =
        "https://sepolia.infura.io/v3/YOUR_INFURA_PROJECT_ID"

    @Provides @Named("contractAddress")
    fun provideContractAddress(): String =
        "0xYOUR_DEPLOYED_CONTRACT_ADDRESS"

    @Provides @Singleton
    fun provideEthereumService(
        @Named("rpcUrl") rpcUrl: String,
        @Named("contractAddress") contractAddress: String
    ): EthereumService = EthereumService(rpcUrl, contractAddress)
}