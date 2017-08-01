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
            setStatus('info', 'Bericht verstuurd!');
        })
        .fail(function(e) {
            var errormsg = e.responseText;
            console.error('failed to submit phone number:', errormsg);
            if (errormsg == 'error:ratelimit') {
                var retryAfter = e.getResponseHeader('Retry-After');
                // TODO: longer durations (minutes, hours, ...)
                setStatus('danger', 'Probeer het opnieuw na ' + retryAfter + ' seconden.');
            } else {
                setStatus('danger', MESSAGES[errormsg]);
            }
        });
}

// copied from irma_email_issuer
function setStatus(alertType, message) {
    $('#status')
        .removeClass('alert-success')
        .removeClass('alert-info')
        .removeClass('alert-warning')
        .removeClass('alert-danger')
        .addClass('alert-'+alertType)
        .text(message)
        .removeClass('hidden');
}

onload();
