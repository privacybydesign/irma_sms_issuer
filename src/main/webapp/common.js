'use strict';

var API_ENDPOINT = '/tomcat/irma_sms_issuer/api/';

// This var is global, as we need it to verify a phone number.
var phone;

$(function() {
    $('#phone-form').on('submit', onSubmitPhone);
    $('#token-form').on('submit', onSubmitToken);

    // Is this a link from a SMS message?
    if (document.location.hash.substr(0, '#!verify:'.length) == '#!verify:') {
        verifyTokenFromURL();
    }

    // Use country names translated into their own language
    var countryData = $.fn.intlTelInput.getCountryData();
    $.each(countryData, function(i, country) {
        country.name = country.name.replace(/.+\((.+)\)/, '$1');
    });

    var telInput = $('#phone');
    telInput.change(validateInput);
    telInput.keyup(validateInput);
    telInput.intlTelInput({
        // geoIpLookup: function(callback) {
        //   $.get('http://ipinfo.io', function() {}, 'jsonp').always(function(resp) {
        //     var countryCode = (resp && resp.country) ? resp.country : '';
        //     callback(countryCode);
        //   });
        // },
        initialCountry: 'nl',
        preferredCountries: ['nl'],
        onlyCountries: ['at', 'pt', 'be', 'bg', 'ic', 'cy', 'dk', 'de', 'ee', 'fo', 'fi', 'fr',
            'gf', 'gi', 'gr', 'gp', 'gg', 'hu', 'ie', 'is', 'im', 'it', 'je', 'hr', 'lv', 'lt',
            'li', 'lu', 'mt', 'mq', 'yt', 'mc', 'nl', 'no', 'at', 'pl', 'pt', 're', 'ro', 'sm',
            'si', 'sk', 'es', 'cz', 'va', 'uk', 'se', 'ch'],
        utilsScript: 'telwidget/js/utils.js'
    });
});

function validateInput() {
    var telInput = $('#phone');
    telInput.removeClass('error');
    if ($.trim(telInput.val())) {
        if (telInput.intlTelInput('isValidNumber')) {
            $('input[type=submit]').prop('disabled', false);
        } else {
            $('input[type=submit]').prop('disabled', true);
            $('#error-msg').removeClass('hide');
            telInput.addClass('error');
        }
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

    phone = $("#phone").intlTelInput("getNumber");
    setStatus('info', MESSAGES['sending-sms']);
    $.post(API_ENDPOINT + 'send', {phone: phone, language: MESSAGES['lang']})
        .done(function(e) {
            console.log('sent SMS:', e);
            var parts = e.split(':');
            // parts[0] should be 'OK'
            var senderNumber = parts[1];
            setStatus('info', MESSAGES['sms-sent'].replace('%number%', senderNumber));
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
                // In JavaScript, we can mostly ignore the fact we're dealing
                // with a string here and treat it as an integer...
                if (retryAfter < 60) {
                    var timemsg = MESSAGES['seconds'];
                    if (retryAfter == 1) {
                        var timemsg = MESSAGES['second'];
                    }
                } else if (retryAfter < 60*60) {
                    retryAfter = Math.round(retryAfter / 60);
                    var timemsg = MESSAGES['minutes'];
                    if (retryAfter == 1) {
                        var timemsg = MESSAGES['minute'];
                    }
                } else {
                    retryAfter = Math.round(retryAfter / 60 / 60);
                    var timemsg = MESSAGES['hours'];
                    if (retryAfter == 1) {
                        var timemsg = MESSAGES['hour'];
                    }
                }
                setStatus('danger', MESSAGES[errormsg].replace('%time%',
                    timemsg.replace('%n%', retryAfter)));
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
        .html(message)
        .removeClass('hidden');
}
