package com.wavesurvivor.horde.roulette;

/** Messages affichés au joueur pour un roulette chest. */
public record RouletteMessages(
        String opening,   // "§6✨ Ouverture..."
        String rolling,   // "§e🎲 La roulette tourne..."
        String reward,    // "§a🎁 Vous avez gagné: §f{reward}"
        String noKey,     // "§cVous avez besoin d'une §6{key} §cpour ouvrir..."
        String consumed   // "§7La clé a été utilisée."
) {}
