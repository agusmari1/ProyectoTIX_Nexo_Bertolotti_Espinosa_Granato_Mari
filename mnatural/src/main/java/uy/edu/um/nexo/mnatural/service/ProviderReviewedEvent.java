package uy.edu.um.nexo.mnatural.service;

import uy.edu.um.nexo.mnatural.entities.Provider;

// Se publica cuando un admin aprueba o rechaza a un prestador.
// El prestador ya trae su nuevo estado (y el motivo, si fue rechazado)
public record ProviderReviewedEvent(Provider provider) {
}
