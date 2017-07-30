'use strict';

var API_ENDPOINT = '/tomcat/irma_sms_issuer/api/';

function onload() {
    $('#phone-form').on('submit', onsubmit);
}

function onsubmit(e) {
    e.preventDefault();
    var phone = $('#phone-form input[type=tel]').val().trim();
    $.post(API_ENDPOINT + 'send-sms-code', {phone: phone})
        .done(function(e) {
            console.log('success', e);
        })
        .fail(function(e) {
            console.error('fail', e.responseText);
        });
}

onload();
