'use strict';

var API_ENDPOINT = '/tomcat/irma_sms_issuer/api/';

var MESSAGES = {
    'sending-sms': 'SMS code wordt verstuurd...',
    'sms-sent': 'Bericht verstuurd!',
    'verifying-token': 'Code wordt geverifieerd...',
    'issuing-credential': 'Credential uitgeven...',
    'phone-add-success': 'Telefoonnummer toegevoegd.',
    'phone-add-timeout': 'Sessie verlopen. Herlaad de pagina om het opnieuw te proberen.',
    'phone-add-cancel': 'Geannuleerd.',
    'phone-add-error': 'Het is helaas niet gelukt dit telefoonnummer toe te voegen aan de IRMA app.',
    'error:internal': 'Interne fout. Neem contact op met IRMA als dit vaker voorkomt.',
    'error:sending-sms': 'Kan de SMS niet verzenden. Dit is waarschijnlijk een probleem in IRMA. Neem contact op met IRMA als dit vaker voorkomt.',
    'error:ratelimit': 'Probeer het opnieuw na %seconds% seconden.',
    'error:cannot-validate-token': 'Kan token niet verifieren. Zit er geen typfout in?',
};

// This var is global, as we need it to verify a phone number.
var phone;

function onload() {
    $('#phone-form').on('submit', onSubmitPhone);
    $('#token-form').on('submit', onSubmitToken);

    // Is this a link from a SMS message?
    if (document.location.hash.substr(0, '#!verify:'.length) == '#!verify:') {
        verifyTokenFromURL();
    }
}

function verifyTokenFromURL() {
    // format: #!verify:+3112345678:ABCDEF
    var parts = document.location.hash.split(':');
    if (parts.length != 3) {
        return;
    }

    if ('history' in window) {
        // remove hash
        history.replaceState(null, '', document.location.pathname);
    }

    phone = parts[1];
    var token = parts[2];

    // Now do the same thing as when a user manually submitted the form
    // Enter the phone number
    $('#phone-form input[type=tel]').val(phone);
    $('#phone-form input').prop('disabled', true);

    // Get a token and fill it here
    $('#token-form input[type=text]').val(token);
    $('#block-token').show();

    // And submit!
    $('#token-form').submit();
}

function onSubmitPhone(e) {
    e.preventDefault();

    // Disable first field
    $('#phone-form input').prop('disabled', true);

    phone = $('#phone-form input[type=tel]').val().trim();
    setStatus('info', MESSAGES['sending-sms']);
    $.post(API_ENDPOINT + 'send', {phone: phone})
        .done(function(e) {
            console.log('sent SMS:', e);
            setStatus('info', MESSAGES['sms-sent']);
            $('#block-token').show();
        })
        .fail(function(e) {
            $('#phone-form input').prop('disabled', false);
            var errormsg = e.responseText;
            console.error('failed to submit phone number:', errormsg);
            if (!errormsg || errormsg.substr(0, 6) != 'error:') {
                errormsg = 'error:internal';
            }
            if (errormsg == 'error:ratelimit') {
                var retryAfter = e.getResponseHeader('Retry-After');
                // TODO: longer durations (minutes, hours, ...)
                setStatus('danger', MESSAGES[errormsg].replace('%seconds%', retryAfter));
            } else {
                setStatus('danger', MESSAGES[errormsg]);
            }
        });
}

function onSubmitToken(e) {
    e.preventDefault();
    var token = $('#token-form input[type=text]').val().trim().toUpperCase();
    setStatus('info', MESSAGES['verifying-token']);
    $.post(API_ENDPOINT + 'verify', {phone: phone, token: token})
        .done(function(jwt) {
            console.log('received JWT:', jwt);
            setStatus('info', MESSAGES['issuing-credential']);
            IRMA.issue(jwt, function(e) {
                setStatus('success', MESSAGES['phone-add-success'])
                console.log('phone added:', e)
            }, function(e) {
                console.warn('cancelled:', e);
                // TODO: don't interpret these strings, use error codes instead.
                if (e === 'Session timeout, please try again') {
                    setStatus('info', MESSAGES['phone-add-timeout'])
                } else { // e === 'User cancelled authentication'
                    setStatus('info', MESSAGES['phone-add-cancel'])
                }
            }, function(e) {
                setStatus('danger', MESSAGES['phone-add-error'])
                console.error('error:', e);
            })
        })
        .fail(function(e) {
            var errormsg = e.responseText;
            console.error('failed to submit phone number:', errormsg);
            if (!errormsg || errormsg.substr(0, 6) != 'error:') {
                errormsg = 'error:internal';
            }
            setStatus('danger', MESSAGES[errormsg]);
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
