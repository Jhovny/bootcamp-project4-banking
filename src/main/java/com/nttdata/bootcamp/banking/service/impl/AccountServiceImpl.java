/**
 * Resumen.
 * Objeto                   : AccountServiceImpl.java
 * Descripción              : Clase para los métodos de la implementación de servicio de la cuenta.
 * Fecha de Creación        : 04/08/2022.
 * Proyecto de Creación     : Bootcamp-01.
 * Autor                    : Marvin Castro.
 * ---------------------------------------------------------------------------------------------------------------------------
 * Modificaciones
 * Motivo                   Fecha             Nombre                  Descripción
 * ---------------------------------------------------------------------------------------------------------------------------
 * Bootcamp-01              05/08/2022        Oscar Candela           Realizar la creación de un método nuevo.
 */

package com.nttdata.bootcamp.banking.service.impl;

import com.nttdata.bootcamp.banking.model.dao.AccountDao;
import com.nttdata.bootcamp.banking.model.document.Account;
import com.nttdata.bootcamp.banking.service.AccountService;
import com.nttdata.bootcamp.banking.service.ClientService;
import com.nttdata.bootcamp.banking.service.ProductService;
import com.nttdata.bootcamp.banking.util.Constant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.UUID;

import static com.nttdata.bootcamp.banking.util.Constant.*;

/**
 * Clase para los métodos de la implementación de servicio de la cuenta.
 */
@Service
public class AccountServiceImpl implements AccountService {

    /** Declaración de la variable de log */
    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

    /** Declaración de la clase dao */
    @Autowired
    private AccountDao accountDao;
    @Autowired
    private ClientService clientService;
    @Autowired
    private ProductService productService;

    /**
     * Método que realiza la acción insertar datos del document
     * @return Mono retorna el Account, tipo Mono
     */
    @Override
    public Mono<Account> insert(Account account) {

        /* Set parameters init */
        account.setAccountNumber(UUID.randomUUID().toString());
        account.setAccountInterbankNumber(UUID.randomUUID().toString());
        account.setCodeAccountState(ACCOUNT_STATE_REQUEST_ACCOUNT);
        account.setDateRegister(new Date());


        // INI JFLORESN Creación de cuenta de ahorro para cliente VIP
        if(account.getCodeProduct().equals(PRODUCT_SAVINGS_VIP_ACCOUNT)) {

            return clientService.findByCode(account.getCodeClient()).flatMap(clientResult -> {

                                if (clientResult.getCodeClientType().equals(CLIENT_TYPE_PERSON)) {

                                    return findByCodeClient(clientResult.getCode())
                                            .filter(a -> a.getCodeProduct().equals(PRODUCT_CREDIT_CARD))
                                            .hasElements()
                                            .flatMap(flag -> {
                                                if (flag) {
                                                    return accountDao.save(account);
                                                } else {

                                                    return Mono.error(new RuntimeException("Estimado usuario, para aperturar una cuenta de ahorro vip se requiere que tenga una cuenta de tarjeta de credito."));
                                                }
                                            });

                                } else {
                                    return Mono.error(new RuntimeException("Se requiere una tarjeta de " +
                                            "crédito para apertura una cuenta de tipo VIP."));
                                }

                            }

                    ).switchIfEmpty( Mono.error(new RuntimeException("Se requiere una tarjeta de " +
                            "crédito para apertura una cuenta de tipo VIP."))
                    ).doFirst(() -> log.info("Begin Insert Account"))
                    .doOnNext(a -> log.info(a.toString()))
                    .doAfterTerminate(() -> log.info("Finish Insert Account"));


        }
        // FIN JFLORESN Creación de cuenta de ahorro para cliente VIP

        // INI JFLORESN Creación de cuenta corriente para cliente pyme
        else if(account.getCodeProduct().equals(PRODUCT_SAVINGS_VIP_ACCOUNT)){


        }
        // FIN JFLORESN Creación de cuenta corriente para cliente pyme

        /* Initiliaze proccess */
        return clientService.findByCode(account.getCodeClient()).flatMap(
                clientResult -> {
                    if(clientResult.getCodeClientType().equals(CLIENT_TYPE_PERSON)) {
                        return productService.findByCode(account.getCodeProduct()).flatMap(productResult -> {
                        if(productResult.getCodeProductType().equals(PRODUCT_TYPE_BANK_ACCOUNTS)) {
                                account.setCreditLine(0.00);
                                account.setAvailableAmount(0.00);
                                return findByCodeClient(clientResult.getCode())
                                        .filter(a -> a.getCodeProduct().equals(productResult.getCode()))
                                        .hasElements()
                                        .flatMap(flag -> {
                                            if(!flag) {
                                                return accountDao.save(account);
                                            }else {
                                                /*return Mono.just(ResponseEntity.badRequest()
                                                        .body("Ya existe una cuenta bancaria de ese tipo para ese cliente"));*/
                                                return Mono.error(new RuntimeException("Ya existe una cuenta bancaria de ese tipo para ese cliente"));
                                            }
                                        });
                            } else if(productResult.getCodeProductType().equals(PRODUCT_TYPE_CREDIT_ACCOUNTS)) {
                                if(account.getCreditLine() > 0) {
                                    account.setAvailableAmount(account.getCreditLine());
                                    if(productResult.getCode().equals(PRODUCT_PERSONAL_CREDIT)) {
                                        return findByCodeClient(clientResult.getCode())
                                                .filter(a -> a.getCodeProduct().equals(productResult.getCode()))
                                                .hasElements()
                                                .flatMap(flag -> {
                                                    if(!flag) {
                                                        return accountDao.save(account);
                                                    }else {
                                                        return Mono.error(new RuntimeException("Ya existe un credito de ese tipo para ese cliente"));
                                                    }
                                                });
                                    } else if(productResult.getCode().equals(PRODUCT_CREDIT_CARD)) {
                                        return accountDao.save(account);
                                    } else {
                                        return Mono.error(new RuntimeException("Cliente Personal solo puede tener un credito personal o Tarjeta de Credito"));
                                    }
                                } else {
                                    return Mono.error(new RuntimeException("Dato línea de credito es requerido"));
                                }
                            } else {
                                return Mono.error(new RuntimeException("No existe codigo tipo de producto"));
                            }
                        });
                    } else if(clientResult.getCodeClientType().equals(CLIENT_TYPE_BUSINESS)) {
                        return productService.findByCode(account.getCodeProduct()).flatMap(productResult -> {

                            if(productResult.getCodeProductType().equals(PRODUCT_TYPE_BANK_ACCOUNTS)) {
                                account.setCreditLine(0.00);
                                account.setAvailableAmount(0.00);
                                if(productResult.getCode().equals(PRODUCT_CURRENT_ACCOUNT)) {
                                    return accountDao.save(account);
                                } else {
                                    return Mono.error(new RuntimeException("Cliente Empresarial Solo puede tener cuentas corrientes"));
                                }
                            } else if(productResult.getCodeProductType().equals(PRODUCT_TYPE_CREDIT_ACCOUNTS)) {
                                if(account.getCreditLine() > 0){
                                    account.setAvailableAmount(account.getCreditLine());
                                    if(productResult.getCode().equals(PRODUCT_BUSINESS_CREDIT) ||
                                            productResult.getCode().equals(PRODUCT_CREDIT_CARD)) {
                                        return accountDao.save(account);
                                    } else {
                                        return Mono.error(new RuntimeException("Cliente Empresarial solo puede tener creditos empresariales y tarjeta de credito"));
                                    }
                                } else {
                                    return Mono.error(new RuntimeException("Dato línea de credito es requerido"));
                                }
                            } else {
                                return Mono.error(new RuntimeException("No existe codigo tipo de producto"));
                            }
                        });
                    } else {
                        return Mono.error(new RuntimeException("El codigo de tipo cliente no existe"));
                    }
                }).doFirst(() -> log.info("Begin Insert Account"))
                .doOnNext(a -> log.info(a.toString()))
                .doAfterTerminate(() -> log.info("Finish Insert Account"));
    }



    /**
     * Método que realiza la acción actualizar datos del document
     * @return Mono retorna el Account, tipo Mono
     */
    @Override
    public Mono<Account> update(Account account) {
        return accountDao.findById(account.getId())
                .doFirst(() -> log.info("Begin Update Account"))
                .map(a -> account)
                .flatMap(this.accountDao::save)
                .doOnNext(a -> log.info(a.toString()))
                .doAfterTerminate(() -> log.info("Finish Update Account"));
    }

    /**
     * Método que realiza la acción borrar datos del document
     * @return Mono retorna el Void, tipo Mono
     */
    @Override
    public Mono<Void> delete(String id) {
        return accountDao.deleteById(id)
                .doFirst(() -> log.info("Begin Delete Account"))
                .doOnNext(a -> log.info(a.toString()))
                .doAfterTerminate(() -> log.info("Finish Delete Account"));
    }

    /**
     * Método que realiza la acción buscar datos por id del document
     * @return Mono retorna el Account, tipo String
     */
    @Override
    public Mono<Account> find(String id) {
        return accountDao.findById(id)
                .doFirst(() -> log.info("Begin Find Account"))
                .doOnNext(a -> log.info(a.toString()))
                .doAfterTerminate(() -> log.info("Finish Find Account"));
    }

    /**
     * Método que realiza la acción buscar datos por código del document
     * @return Mono retorna el Account, tipo String
     */
    @Override
    public Mono<Account> findByAccountNumber(String accountNumber) {
        return accountDao.findByAccountNumber(accountNumber)
                .doFirst(() -> log.info("Begin FindByAccountNumber Account"))
                .doOnNext(a -> log.info(a.toString()))
                .doAfterTerminate(() -> log.info("Finish FindByAccountNumber Account"));
    }

    @Override
    public Flux<Account> findByCodeClient(String code) {
        return accountDao.findByCodeClient(code)
                .doFirst(() -> log.info("Begin FindByCodeClient Account"))
                .doOnNext(a -> log.info(a.toString()))
                .doAfterTerminate(() -> log.info("Finish FindByCodeClient Account"));
    }

    /**
     * Método que realiza la acción buscar todos los datos del document
     * @return Mono retorna el Account, tipo String
     */
    @Override
    public Flux<Account> findAll() {
        return accountDao.findAll()
                .doFirst(() -> log.info("Begin FindAll Account"))
                .doOnNext(a -> log.info(a.toString()))
                .doAfterTerminate(() -> log.info("Finish FindAll Account"));
    }

}

